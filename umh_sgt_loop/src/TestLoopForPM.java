
public class TestLoopForPM {
	public static void main(String args[]) {

		if (false) {
			// only run once
			SendSMSLoopForPM pm = new SendSMSLoopForPM();
			pm.beginToCheck();
		} else {
			// keep running
			SendSMSLoopForPM pm = new SendSMSLoopForPM();
			Thread t = new Thread(pm);
			t.start();
		}

	}
}

// System.out.println(wonum);
// System.out.println("begin:"+beginSendSMSCheckPointTimeMillistTime);
// System.out.println("nowMillistTime:"+nowMillistTime);
// System.out.println("end:"+endSendSMSCheckPointTimeMillistTime);
//
//
// System.out.println("now-begin:"+(nowMillistTime-beginSendSMSCheckPointTimeMillistTime)/3600000);
// System.out.println("end-now:"+(endSendSMSCheckPointTimeMillistTime-nowMillistTime)/3600000);
//

