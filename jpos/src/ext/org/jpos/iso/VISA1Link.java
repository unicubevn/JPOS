/*
 * $Log$
 * Revision 1.4  1999/12/15 16:07:37  apr
 * Protection against negative timeouts on readUntil
 *
 * Revision 1.3  1999/12/14 21:56:54  apr
 * VISA1 links seems to work better when LRC is calculated correctly :blush:
 *
 * Revision 1.2  1999/11/25 17:01:09  apr
 * Added getModem() method
 *
 * Revision 1.1  1999/11/24 18:08:52  apr
 * Added VISA 1 Support
 *
 */

package uy.com.cs.jpos.iso;

import java.io.*;
import java.util.*;
import javax.comm.*;

/**
 * Implements client-side VISA-1 Link protocol operating in a
 * ISOMUX-like way (you can queue ISORequests)
 *
 * @author apr@cs.com.uy
 * @version $Id$
 * @see ISORequest
 * @see ISOException
 * @see V24
 * @see Modem
 * @see <a href="http://www.frii.com/~jarvi/rxtx/">CommAPI</a>
 */
public class VISA1Link implements LogProducer, Runnable
{
    Logger logger;
    String realm;
    Vector txQueue;
    V24 v24;
    Modem mdm;
    int currentState;
    long timeout;
    boolean waitENQ;
    ISOPackager packager;

    public static final byte SOH = 001;
    public static final byte STX = 002;
    public static final byte ETX = 003;
    public static final byte EOT = 004;
    public static final byte ENQ = 005;
    public static final byte ACK = 006;
    public static final byte DLE = 020;
    public static final byte NAK = 025;

    /**
     * @param v24 instance
     * @param mdm Modem instance
     * @param packager custom VISA1 packager
     */
    public VISA1Link (V24 v24, Modem mdm, ISOPackager packager) {
	super();
	txQueue  = new Vector();
	this.v24 = v24;
	this.mdm = mdm;
	this.packager = packager;
	setDefaults();
    }
    /**
     * @param v24 instance
     * @param mdm Modem instance
     * @param packager custom VISA1 packager
     * @param logger a logger
     * @param realm  logger's realm
     */
    public VISA1Link 
	(V24 v24, Modem mdm, ISOPackager packager, Logger logger, String realm)
    {
	super();
	setLogger (logger, realm);
	txQueue  = new Vector();
	this.v24 = v24;
	this.mdm = mdm;
	this.packager = packager;
	setDefaults();
    }
    public Modem getModem () {
	return mdm;
    }
    private void setDefaults() {
	this.timeout  = 60000;
	this.waitENQ  = true;
    }
    public void setLogger (Logger logger, String realm) {
	this.logger = logger;
	this.realm  = realm;
    }
    public String getRealm () {
	return realm;
    }
    public Logger getLogger() {
	return logger;
    }
    /**
     * @param waitENQ true if should wait for ENQ
     */
    public void setWaitENQ (boolean waitENQ) {
	this.waitENQ = waitENQ;
    }
    /**
     * @param timeout (per request)
     */
    public void setTimeout (long timeout) {
	this.timeout = timeout;
    }
    public long getTimeout () {
	return timeout;
    }

    private byte calcLRC (byte[] b) {
	byte chk = ETX;
	for(int i=0; i<b.length; i++)
	    chk ^= b[i];
	return chk;
    }

    private void sendPacket (byte[] b, LogEvent evt) throws IOException {
	// avoid multiple calls to v24.send() in order to show
	// the whole frame within one LogEvent
	byte[] frame = new byte [b.length + 3];
	frame[0] = STX;
	System.arraycopy (b, 0, frame, 1, b.length);
	frame[b.length+1] = ETX;
	frame[b.length+2] = calcLRC (b);
	v24.send (frame);
	v24.flushTransmitter();
	evt.addMessage ("<send>"+ISOUtil.dumpString(frame)+"</send>");
    }

    private byte[] receivePacket (long timeout, LogEvent evt) 
	throws IOException
    {
	String end     = "\001\002\003\004\005\006\020\025";
	String packet  = v24.readUntil (end, timeout, true);
	String payload = null;
	if (packet.length() > 2 && packet.charAt (packet.length()-1) == ETX) {
	    payload = packet.substring (0, packet.length() - 1);
	    byte lrc  = calcLRC (payload.getBytes());
	    byte[] receivedLrc = new byte[1];
	    int c = v24.read (receivedLrc, 2000);
	    if (c != 1 || lrc != receivedLrc[0])
		payload = null;
	}
	return payload.getBytes();
    }

    synchronized public byte[] request (byte[] request) 
	throws IOException
    {
	String buf;
	byte[] response = null;
	long timeout = this.timeout;
	long start   = System.currentTimeMillis();
	long expire  = start + timeout;
	int state    = waitENQ ? 0 : 1;
	LogEvent evt = new LogEvent (this, "request");
	v24.flushReceiver();
	while ( (timeout = (expire - System.currentTimeMillis())) > 0
			&& response == null && mdm.isConnected()) 
	{
	    long elapsed = System.currentTimeMillis() - start;
	    System.out.println ("-------------> state:" + state);
	    switch (state) {
		case 0:
		    evt.addMessage ("<enq>" + elapsed + "</enq>");
		    buf = v24.readUntil ("\005", timeout, true);
		    if (buf.endsWith ("\005") )
			state++;
		    break;
		case 1:
		    evt.addMessage ("<tx>" + elapsed + "</tx>");
		    System.out.println ("[Transmiting]");
		    sendPacket (request, evt);
		    System.out.println (
			"[waiting for ACK or NAK] timeout="+timeout);
		    buf = v24.readUntil ("\006\025", timeout, true);
		    System.out.println ("[end readUntil]");
		    if (buf.endsWith ("\006")) {
			System.out.println ("[Got ACK]");
			state++;
		    }
		    break;
		case 2:
		    evt.addMessage ("<rx>" + elapsed + "</rx>");
		    response = receivePacket (timeout, evt);
		    v24.send (response == null ? NAK : ACK);
		    break;
	    }
	}
	if (mdm.isConnected() && response == null) {
	    // v24.send (EOT);
	    evt.addMessage ("<eot/>");
	}
	evt.addMessage ("<rx>"+(System.currentTimeMillis() - start)+"</rx>");
	Logger.log (evt);
	return response;
    }

    private void doTransceive () throws IOException, ISOException
    {
	Object o = txQueue.firstElement();
	ISOMsg m = null;
	ISORequest r = null;

    	if (o instanceof ISORequest) {
	    r = (ISORequest) o;
	    if (!r.isExpired())  {
		m = r.getRequest();
		r.setTransmitted ();
	    }
	} else if (o instanceof ISOMsg) {
	    m = (ISOMsg) o;
	}
	if (m != null) {
	    m.setPackager (packager);
	    byte[] response = request (m.pack());
	    // response = "APROBADO 12313150 POSITIVO".getBytes();
	    if (r != null) {
		ISOMsg resp = (ISOMsg) m.clone();
		resp.set (new ISOField (0, "0110"));
		resp.unpack (response);
		r.setResponse (resp);
	    }
	}
	txQueue.removeElement(o);
	txQueue.trimToSize();
    }

    public void run () {
	for (;;) {
	    try {
		for (;;) {
		    while (txQueue.size() == 0 || !mdm.isConnected()) {
			synchronized(this) {
			    this.wait (10000);
			}
		    }
		    doTransceive ();
		}
	    } catch (InterruptedException e) { 
		Logger.log (new LogEvent (this, "mainloop", e));
	    } catch (Exception e) {
		Logger.log (new LogEvent (this, "mainloop", e));
		try {
		    Thread.sleep (10000); // just in case (repeated Exception)
		} catch (InterruptedException ex) { }
	    }
	}
    }

    synchronized public void queue(ISORequest r) {
	txQueue.addElement(r);
	this.notify();
    }

    public static void main (String[] args) {
	Logger l = new Logger();
	l.addListener (new SimpleLogListener (System.out));
	try {
	    V24 v24 = new V24 ("/dev/ttyS1", l, "V24");
	    v24.setSpeed (300,
                SerialPort.DATABITS_7,
                SerialPort.STOPBITS_1,
                SerialPort.PARITY_EVEN,
                SerialPort.FLOWCONTROL_RTSCTS_IN |
                    SerialPort.FLOWCONTROL_RTSCTS_OUT
	    );
	    SimpleDialupModem mdm = new SimpleDialupModem (v24);
	    mdm.setDialPrefix ("S11=50DT");
	    int[] sequence = { 41, 35, 4, 48 };
	    VISA1Packager packager = new VISA1Packager 
		(sequence, 63, "05", "APROBADO");
	    packager.setLogger (l, "VISA1Packager");
	    VISA1Link link = new VISA1Link (v24, mdm, packager);
	    link.setWaitENQ (false);
	    link.setTimeout (60000);
	    Thread t = new Thread (link);
	    t.start();
	    Thread.sleep (1000);

	    mdm.dial ("<your phone number>", 45000);    // CS tty8

	    ISOMsg m = new ISOMsg();
	    m.set (new ISOField (0, "0100"));
	    m.set (new ISOField (2,"4300000000000001"));
	    m.set (new ISOField (4, "1"));
	    m.set (new ISOField (14,"0112"));
	    // m.set (new ISOField (35,"4300000000000001=0012"));
	    m.set (new ISOField (41, "3000300"));
	    m.set (new ISOField (48, "1"));
	    m.set (new ISOField (49, "858"));
	    m.dump (System.out, "--->");
	    m.setPackager (packager);
	    System.out.println ("dump:" +ISOUtil.dumpString (m.pack()));
	    ISORequest r = new ISORequest (m);
	    link.queue (r);
	    ISOMsg resp = r.getResponse (60000);
	    resp.dump (System.out, "<---");

	    mdm.hangup();
	    v24.close();
	} catch (Throwable e) {
	    e.printStackTrace();
	}
    }
}
