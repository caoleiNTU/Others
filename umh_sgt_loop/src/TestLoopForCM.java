
public class TestLoopForCM {
	public static void main(String args[]) {
		if (false) {
			// only run once
			SendSMSLoopForCM cm = new SendSMSLoopForCM();
			cm.beginToCheck();
		} else {
			// keep running
			SendSMSLoopForCM cm = new SendSMSLoopForCM();
			Thread t = new Thread(cm);
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

