import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.Vector;

import org.apache.log4j.Logger;

/*
 * @Date 17 June 2014
 * UMH team
 * Author:CL
 * NOT USED (see SGT UNA vB)
 */

public class SendSMSLoopForPM extends Thread {

	private static Connection connection = null;
	private static String serverName = new String();
	private static String portNumber = new String();
	private static String sid = new String();
	private static String DBusername = new String();
	private static String encDbPassword = new String();
	private static String decDbPassword = new String();
	private static String encPassword = new String();
	private static String thresholdOfSMS_Day_PM = new String();

	private static String sleepTime = "1000";
	private static final Logger logger = Logger.getLogger(SendSMSLoopForPM.class);

	public void run() {
		while (true) {
			beginToCheck();
			try {
				Thread.sleep(Long.parseLong(sleepTime));
			} catch (NumberFormatException e) {
				logger.error("Number Format Exception!");
			} catch (InterruptedException e) {
				logger.error("Thread is interrupt!");
				e.printStackTrace();
			}
		}
	}

	public static void GetPropertyFile() {
		try {

			logger.debug("GetProperties");
			/*
			 * Get the time interval of each message from properties file in
			 * umh.properties
			 */
			Properties pt = new Properties();
			InputStream configStream = new FileInputStream("umh.properties");

			pt.load(configStream);
			pt.getProperty("PTY_INT_HOST");
			pt.getProperty("PTY_INT_USERNAME");
			encPassword = pt.getProperty("PTY_ENCMAXIMOLOGIN");
			byte[] dec = new sun.misc.BASE64Decoder().decodeBuffer(encPassword);
			new String(dec, "UTF8");
			sleepTime = pt.getProperty("PTY_SLEEP_TIME");
			serverName = pt.getProperty("PTY_DBSERVERNAME");
			portNumber = pt.getProperty("PTY_DBPORTNUMBER");
			sid = pt.getProperty("PTY_DBSID");
			DBusername = pt.getProperty("PTY_DBUSERNAME");
			thresholdOfSMS_Day_PM = pt.getProperty("PTY_SMS_THRESHOLD_DAYS_PM");

			encDbPassword = pt.getProperty("PTY_ENCUMHDBLOGIN");
			if (encDbPassword != null) {
				dec = new sun.misc.BASE64Decoder().decodeBuffer(encDbPassword);
				decDbPassword = new String(dec, "UTF8");
			}

			configStream.close();
		} catch (IOException ioe) {
			logger.error("(SMS_LOOP_FOR_PM) IO Exception :", ioe);
		}
	}

	// The main function to check the WO and insert the SMS sending information
	// into UMH_SMS_TEMP_T
	public void beginToCheck() {
		PreparedStatement Prestatement = null;
		ResultSet resultset = null;
		PreparedStatement pstmt = null;
		ResultSet rset3 = null;

		GetPropertyFile();
		String driverName = "oracle.jdbc.driver.OracleDriver";
		try {
			Class.forName(driverName);
			/*
			 * Create a connection to the table
			 */

			String url = "jdbc:oracle:thin:@" + serverName + ":" + portNumber + ":" + sid;
			connection = DriverManager.getConnection(url, DBusername, decDbPassword);
			Statement statement = connection.createStatement();

			/*
			 * Alter session for inserting into Date column type in the database
			 */
			String SQLstatement = new String();
			SQLstatement = "ALTER SESSION SET NLS_DATE_FORMAT = 'YYYY-MM-DD HH24:MI:SS'";
			statement.executeUpdate(SQLstatement);

			if (statement != null) {
				statement.close();
				statement = null;
			}

			/*
			 * Get System Date/Time
			 */
			pstmt = connection.prepareStatement("SELECT TO_CHAR(sysdate,'YYYY-MM-DD HH24:MI:SS') FROM DUAL");
			ResultSet rset1 = pstmt.executeQuery();

			String datetime = "";
			if (rset1.next()) {
				datetime = rset1.getString(1);
			}
			if (pstmt != null) {
				pstmt.close();
			}
			if (rset1 != null) {
				rset1.close();
				rset1 = null;
			}

			String Site = "SGT";

			// -------------------------------------------------------------------------------------------------------------

			// Get all the related maintenance and contractor's phone numbers
			Vector mobilePhones = new Vector();

			String phonesql = "select a.usrname, b.mobilephone from maximo.maxusergroups a, maximo.labor b where (a.grpname='GP_SGT_MAINT' or "
					+ "a.grpname='GP_SGT_CON') and a.usrname=b.laborcode and b.siteid='" + Site + "'";
			pstmt = connection.prepareStatement(phonesql);

			ResultSet resultPhone = pstmt.executeQuery();
			// Put the related person's phone number into the list
			while (resultPhone.next()) {
				mobilePhones.add(resultPhone.getString(2));
			}
			if (resultPhone != null) {
				resultPhone.close();
				resultPhone = null;
			}

			String Maint_phonesql = "select mobilephone from maximo.labor where laborcode in (select distinct respparty from maximo.WOASSIGNMNTPARTY where WOASSIGNMNTqueueid= 'GP_SGT_USER') and siteid='"
					+ Site + "'";
			pstmt = connection.prepareStatement(Maint_phonesql);
			ResultSet Maint_resultPhone = pstmt.executeQuery();
			// Put the related person's phone number into the list
			while (Maint_resultPhone.next()) {
				mobilePhones.add(Maint_resultPhone.getString(1));
			}
			if (Maint_resultPhone != null) {
				Maint_resultPhone.close();
				Maint_resultPhone = null;
			}

			// -------------------------------------------------------------------------------------------------------------

			SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

			logger.debug("(SMS_LOOP_FOR_PM) Start fetch from database ; " + new java.util.Date());

			String sql = "SELECT WORKORDER.WONUM,PM.DUR_MAX_DAY,Workorder.FAULT_DETECT_DT,WORKORDER.ALLOW_RESP_TM,WORKORDER.NOTIFY_DT,ALLOW_DOWN_TM FROM MAXIMO.WORKORDER,MAXIMO.PM WHERE MAXIMO.WORKORDER.pmnum = MAXIMO.pm.pmnum and WORKORDER.PARENT IS NULL AND WORKORDER.WORKTYPE = 'PM' AND WORKORDER.SITEID = '"
					+ Site + "' AND WORKORDER.STATUS NOT IN ('WAPPR','COMP','CLOSE','CAN')";
			pstmt = connection.prepareStatement(sql);
			rset3 = pstmt.executeQuery();
			while (rset3.next()) {
				String wonum = rset3.getString(1);
				int PmMaxDuration = rset3.getInt(2);
				String FalutDate = rset3.getString(3);

				Calendar FaultCalendar = null;
				FaultCalendar = Calendar.getInstance();
				Date fault_dt = sdf1.parse(FalutDate);
				FaultCalendar.setTime(fault_dt);

				Calendar nowCalendar = null;
				nowCalendar = Calendar.getInstance();

				Date dt = sdf1.parse(datetime);
				nowCalendar.setTime(dt);

				long nowMillistTime = nowCalendar.getTime().getTime();
				long FaultMillisTime = FaultCalendar.getTime().getTime();
				long betweenMillistTime = nowMillistTime - FaultMillisTime;

				int betweenDay = 0;
				if (betweenMillistTime < 0)
					betweenDay = 0;
				else {
					betweenDay = (int) (betweenMillistTime / (1000 * 3600 * 24));
				}
				int finalDay = PmMaxDuration - betweenDay - 1;

				final StringBuffer message = new StringBuffer();
				message.append("#").append(wonum);
				message.append("#").append("WO will be due to maximum duration time in " + finalDay + " days");

				// Do checks on existing workorder and mobile phone number
				boolean isSent = false;
				final StringBuffer sqlQuery1 = new StringBuffer(
						"SELECT COUNT(1) FROM UMHINTERFACE.UMH_SMS_TEMP_T WHERE MESSAGE LIKE '%").append(wonum).append(
						"%' AND UPPER(NOTIFY_TYPE) = 'ONCE' ");
				Prestatement = connection.prepareStatement(sqlQuery1.toString());

				resultset = Prestatement.executeQuery();
				if (resultset.next()) {

					isSent = resultset.getInt(1) > 0 ? true : false;
				}

				if (resultset != null) {
					resultset.close();
					resultset = null;
				}

				if (!isSent) {

					long endSendSMSCheckPointTimeMillistTime = (long) (FaultMillisTime + PmMaxDuration * 24 * 3600 * 1000);
					long beginSendSMSCheckPointTimeMillistTime = 0;

					if (Integer.parseInt(thresholdOfSMS_Day_PM) >= PmMaxDuration) {
						beginSendSMSCheckPointTimeMillistTime = FaultMillisTime;
					} else {
						beginSendSMSCheckPointTimeMillistTime = (long) (FaultMillisTime + (PmMaxDuration * 24 * 3600000 - Integer
								.parseInt(thresholdOfSMS_Day_PM) * 24 * 3600 * 1000));
					}

					// check whether the time satisfy the threshold
					if ((beginSendSMSCheckPointTimeMillistTime < nowMillistTime)
							&& (nowMillistTime < endSendSMSCheckPointTimeMillistTime)) {

						/*
						 * Connect to MAXIMO Database to retrieve the Contractor
						 * Phone No. Copy it into String array. Save each phone
						 * number into UMH_SMS_TEMP_T table
						 */

						logger.debug("******Insert new SMS message into the table!");
						final StringBuffer sqlQuery2 = new StringBuffer(
								"INSERT INTO UMHINTERFACE.UMH_SMS_TEMP_T VALUES(?, ?, ?, ?, ?)");
						Prestatement = connection.prepareStatement(sqlQuery2.toString());

						for (int index = 0; index < mobilePhones.size(); index++) {
							Prestatement.setString(1, datetime);
							Prestatement.setString(2, message.toString());
							Prestatement.setString(3, mobilePhones.get(index).toString());
							Prestatement.setString(4, "false");
							Prestatement.setString(5, "ONCE");

							int result = Prestatement.executeUpdate();
							logger.debug("SGTWONotification inserted row=" + result);
						}

						connection.commit();
					} else {
						logger.debug("DB has this item before: ");
						logger.debug("SGTWONotification have sent the SMS once for " + wonum + ".");
					}
				}
			} // end of checking the threshold

		} catch (ClassNotFoundException e) {
			logger.error("Class not found!");
			e.printStackTrace();
		} catch (SQLException e) {
			logger.error("Sql Exception!");
			e.printStackTrace();
		} catch (ParseException e) {
			logger.error("Parse Exception!");
			e.printStackTrace();
		} finally {

			try {
				if (Prestatement != null) {
					Prestatement.close();
					Prestatement = null;
				}
				if (pstmt != null) {
					pstmt.close();
					pstmt = null;
				}
			} catch (Exception exception4) {
			}
			try {
				if (rset3 != null)
					rset3.close();
				rset3 = null;
			} catch (SQLException e) {
				logger.error("Sql Exception!");
				e.printStackTrace();
			}

			if (resultset != null) {
				try {
					resultset.close();
					resultset = null;
				} catch (SQLException e) {
					logger.error("Sql Exception!");
					e.printStackTrace();
				}
			}
			if (connection != null) {
				try {
					connection.close();
					connection = null;
				} catch (SQLException e) {
					logger.error("Sql Exception!");
					e.printStackTrace();
				}
			}

			System.gc();
		}

	}

}
