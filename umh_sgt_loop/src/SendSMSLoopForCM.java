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
 */

public class SendSMSLoopForCM extends Thread {

	private static Connection connection = null;
	private static String serverName = new String();
	private static String portNumber = new String();
	private static String sid = new String();
	private static String DBusername = new String();
	private static String encDbPassword = new String();
	private static String decDbPassword = new String();
	private static String encPassword = new String();
	private static String thresholdOfSMS_CM = new String();

	private static String sleepTime = "1000";
	private static final Logger logger = Logger.getLogger(SendSMSLoopForCM.class);

	public void run() {
		while (true) {
			beginToCheck();
			try {
				Thread.sleep(Integer.parseInt(sleepTime));
			} catch (Exception e) {
				logger.error("[SendSMSLoopForCM] Encountered Exception while running the thread", e);
				e.printStackTrace();
			}
		}
	}

	public static void GetPropertyFile() {
		try {

			logger.debug("[SendSMSLoopForCM] GetProperties");
			/*
			 * Get the time interval of each message from properties file in
			 * umh.properties
			 */
			final Properties pt = new Properties();
			final InputStream configStream = new FileInputStream("config/umh.properties");

			pt.load(configStream);
			pt.getProperty("PTY_INT_HOST");
			pt.getProperty("PTY_INT_USERNAME");
			encPassword = pt.getProperty("PTY_ENCMAXIMOLOGIN");
			byte[] dec = new sun.misc.BASE64Decoder().decodeBuffer(encPassword);
			sleepTime = pt.getProperty("PTY_SLEEP_TIME");
			serverName = pt.getProperty("PTY_DBSERVERNAME");
			portNumber = pt.getProperty("PTY_DBPORTNUMBER");
			sid = pt.getProperty("PTY_DBSID");
			DBusername = pt.getProperty("PTY_DBUSERNAME");
			thresholdOfSMS_CM = pt.getProperty("PTY_SMS_THRESHOLD_CM");
			encDbPassword = pt.getProperty("PTY_ENCUMHDBLOGIN");

			if (encDbPassword != null) {
				dec = new sun.misc.BASE64Decoder().decodeBuffer(encDbPassword);
				decDbPassword = new String(dec, "UTF8");
			}

			configStream.close();

		} catch (IOException ioe) {
			logger.error("[SendSMSLoopForCM] Encountered IO Exception :", ioe);
		}
	}

	// The main function to check the WO and insert the SMS sending information
	// into UMH_SMS_TEMP_T
	public void beginToCheck() {
		final String Site = "SGT";

		PreparedStatement prestatement = null;
		ResultSet resultset = null;
		PreparedStatement pstmt = null;
		ResultSet rset3 = null;

		GetPropertyFile();
		final String driverName = "oracle.jdbc.driver.OracleDriver";
		try {
			Class.forName(driverName);

			/*
			 * Create a connection to the table
			 */
			final String url = "jdbc:oracle:thin:@" + serverName + ":" + portNumber + ":" + sid;
			connection = DriverManager.getConnection(url, DBusername, decDbPassword);
			Statement statement = connection.createStatement();
			/*
			 * Alter session for inserting into Date column type in the database
			 */
			final String SQLstatement = "ALTER SESSION SET NLS_DATE_FORMAT = 'YYYY-MM-DD HH24:MI:SS'";
			statement.executeUpdate(SQLstatement);

			if (statement != null) {
				statement.close();
			}
			statement = null;

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

			// -------------------------------------------------------------------------------------------------------------

			// Get all the related maintenance and contractor's phone numbers
			final Vector mobilePhones = new Vector();

			final String phonesql = "select a.usrname, b.mobilephone from maximo.maxusergroups a, maximo.labor b where (a.grpname='GP_SGT_MAINT' or "
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

			final String Maint_phonesql = "select mobilephone from maximo.labor where laborcode in (select distinct respparty from maximo.WOASSIGNMNTPARTY where WOASSIGNMNTqueueid= 'GP_SGT_USER') and siteid='"
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

			final SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

			logger.info("[SendSMSLoopForCM] Start fetch from database ; " + new java.util.Date());

			// for SGT, there is no INPRG status for contractor then from ACK -> JOBDONE and suppose contractor should
			// enter "Arrival Date and Time"
			final String sql = "SELECT WONUM,ALLOW_RESP_TM,NOTIFY_DT,ALLOW_DOWN_TM,FAULT_DETECT_DT FROM MAXIMO.WORKORDER WHERE PARENT IS NULL AND WORKTYPE = 'CM' AND SITEID = '"
					+ Site + "' AND STATUS NOT IN ('WAPPR','COMP','CLOSE','CAN')";
			pstmt = connection.prepareStatement(sql);

			rset3 = pstmt.executeQuery();
			while (rset3.next()) {
				final String wonum = rset3.getString(1);
				final float float_allowResponseTime = rset3.getFloat(2);
				final String faultDetectDate = rset3.getString(5);

				// -------------------------------------------------------------
				// Adjust the display of the allowResponseTime. If the time is
				// yy.xx, the time will be yy.xx. But if the time is yy.00, the
				// time will be only yy.
				// If the time is 0.xxx, the allowResponseTime will be 0.xxx.

				String allowResponseTime = "" + float_allowResponseTime;

				// Simplified version
				if ((float_allowResponseTime % 1) == 0) {
					// if true and > 0
					allowResponseTime = "" + (new Float(float_allowResponseTime).intValue());
				}

				final StringBuffer message = new StringBuffer();
				message.append("#").append(wonum);
				message.append("#").append(
						"WO will be due to allowable response/repair time in " + allowResponseTime + " hours");

				// Do checks on existing workorder and mobile phone number
				boolean isSent = false;
				final StringBuffer sqlQuery1 = new StringBuffer(
						"SELECT COUNT(1) FROM UMHINTERFACE.UMH_SMS_TEMP_T WHERE MESSAGE LIKE '%").append(wonum).append(
						"%' AND UPPER(NOTIFY_TYPE) = 'ONCE' ");
				prestatement = connection.prepareStatement(sqlQuery1.toString());
				resultset = prestatement.executeQuery();
				if (resultset.next()) {
					isSent = resultset.getInt(1) > 0 ? true : false;
				}

				if (resultset != null) {
					resultset.close();
					resultset = null;
				}

				if (!isSent) {
					// This is to compute the threshold.
					final Calendar faultCalendar = Calendar.getInstance();
					final Date fault_dt = sdf1.parse(faultDetectDate);
					faultCalendar.setTime(fault_dt);

					final Calendar nowCalendar = Calendar.getInstance();
					final Date dt = sdf1.parse(datetime);
					nowCalendar.setTime(dt);

					final long nowMillistTime = nowCalendar.getTime().getTime();
					final long faultMillisTime = faultCalendar.getTime().getTime();
					final long endSendSMSCheckPointTimeMillistTime = (long) (faultMillisTime + float_allowResponseTime * 3600000);
					long beginSendSMSCheckPointTimeMillistTime = 0;

					float tmpthreshold = 0.0f;
					try {
						tmpthreshold = (thresholdOfSMS_CM != null) && !thresholdOfSMS_CM.equals("") ? Float
								.parseFloat(thresholdOfSMS_CM) : 0.0f;
					} catch (NumberFormatException nue) {
						logger.error(
								"[SendSMSLoopForCM] Encountered NumberFormatException Exception about converting the thresholdOfSMS_CM to numbers :",
								nue);
					}
					final float threshold = tmpthreshold;

					if (threshold >= float_allowResponseTime) {
						beginSendSMSCheckPointTimeMillistTime = faultMillisTime;
					} else {
						beginSendSMSCheckPointTimeMillistTime = (long) (faultMillisTime + (float_allowResponseTime - threshold) * 3600000);
					}
					// -------------------------------------------------------------

					// Check whether the time satisfy the threshold
					if ((beginSendSMSCheckPointTimeMillistTime < nowMillistTime)
							&& (nowMillistTime < endSendSMSCheckPointTimeMillistTime)) {

						/*
						 * Connect to MAXIMO Database to retrieve the Contractor
						 * Phone No. Copy it into String array. Save each phone
						 * number into UMH_SMS_TEMP_T table
						 */

						logger.info("[SendSMSLoopForCM]  ******Insert new SMS message into the table.");
						final StringBuffer sqlQuery2 = new StringBuffer(
								"INSERT INTO UMHINTERFACE.UMH_SMS_TEMP_T VALUES(?, ?, ?, ?, ?)");
						prestatement = connection.prepareStatement(sqlQuery2.toString());
						for (int index = 0; index < mobilePhones.size(); index++) {
							final String phoneNo = mobilePhones.get(index).toString();
							prestatement.setString(1, datetime);
							prestatement.setString(2, message.toString());
							prestatement.setString(3, phoneNo);
							prestatement.setString(4, "false");
							prestatement.setString(5, "ONCE");

							int result = prestatement.executeUpdate();
							logger.info("[SendSMSLoopForCM] SGTWONotification inserted phone[" + phoneNo + "] row="
									+ result);
						}

						connection.commit();
					} else {
						logger.info("[SendSMSLoopForCM] SGTWONotification have sent the SMS once for " + wonum + ".");
					}
				} // End of checking the threshold

			}
		} catch (ClassNotFoundException e) {
			logger.error("[SendSMSLoopForCM] Encountered ClassNotFound Exception=", e);
			e.printStackTrace();
		} catch (SQLException e) {
			logger.error("[SendSMSLoopForCM] Encountered SQL Exception=", e);
			e.printStackTrace();
		} catch (ParseException e) {
			logger.error("[SendSMSLoopForCM] Encountered Parse Exception=", e);
			e.printStackTrace();
		} finally {
			try {
				if (prestatement != null) {
					prestatement.close();
					prestatement = null;
				}

				if (pstmt != null) {
					pstmt.close();
					pstmt = null;
				}

			} catch (Exception exception4) {
			}
			try {
				if (rset3 != null) {
					rset3.close();
					rset3 = null;
				}

			} catch (SQLException e) {
				logger.error("[SendSMSLoopForCM] Encountered SQL Exception=", e);
				e.printStackTrace();
			}

			try {
				if (resultset != null) {
					resultset.close();
					resultset = null;
				}

			} catch (SQLException e) {
				logger.error("[SendSMSLoopForCM] Encountered SQL Exception=", e);
				e.printStackTrace();
			}

			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (SQLException e) {
				logger.error("[SendSMSLoopForCM] Encountered SQL Exception=", e);
				e.printStackTrace();
			}

			System.gc();
		}
	}
}
