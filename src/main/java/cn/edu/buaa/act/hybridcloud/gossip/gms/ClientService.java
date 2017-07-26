package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.config.GossiperDescriptor;
import org.apache.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

/**
 * Created by toler on 2016/12/6.
 */
public class ClientService implements Runnable {
    private static Logger logger = Logger.getLogger(ClientService.class);

    private Socket socket;
    private boolean run = true;
    private long lastReceiveTime = System.currentTimeMillis();

    public ClientService(Socket socket) {
        assert (socket != null);
        this.socket = socket;
        try {
            socket.setReceiveBufferSize(100000);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        if (System.currentTimeMillis() - this.lastReceiveTime > GossiperDescriptor.getRpcTimeout()) {
            close();
        } else {
            DataInputStream inputStream;
            DataOutputStream outputStream;
            try {
                inputStream = new DataInputStream(this.socket.getInputStream());
                outputStream = new DataOutputStream(this.socket.getOutputStream());
                String name = new String();
                while (this.run) {
                    //if(inputStream.available() > 0){
                    //System.out.println("available:" + inputStream.available());
                    this.lastReceiveTime = System.currentTimeMillis();
                    int cmd = inputStream.readInt();
                    int size = inputStream.readInt();
                    //System.out.println("cmd: "+ cmd + "   length:" + size);
                    if (cmd == 0 && size == 8) {
                        outputStream.writeInt(cmd);
                        outputStream.writeInt(size);
                        System.out.println("nop heart!");
                    } else if (cmd == 1) {
                        size -= 8;
                        int nleng = inputStream.readInt();
                        size -= 4;
                        byte[] fileNameBuf = new byte[nleng];
                        inputStream.readFully(fileNameBuf, 0, nleng);
                        size -= nleng;
                        long offset = inputStream.readLong();
                        size -= 8;
                        int bufSize = inputStream.readInt();
                        size -= 4;
                        byte[] buf = new byte[bufSize];
                        inputStream.readFully(buf, 0, bufSize);
                        size -= bufSize;
                        if (size > 0) {
                            inputStream.skipBytes(size);
                            System.out.println("remaining:---------" + size);
                        }
                        final String fileName = new String(fileNameBuf);
                        System.out.println("cmd: " + cmd +
                                "\nsize: " + size +
                                "\nnleng: " + nleng +
                                "\nfileName: " + fileName +
                                "\noffset: " + offset +
                                "\nbufSize: " + bufSize +
                                "\nbuf: " + buf.toString());

                        ClientServer.instance.addLogToReceived(fileName, offset, buf);
                        //System.out.println(synFile);
                        //System.out.println("cmd: "+ cmd + "   length:" + size);
                        outputStream.writeInt(cmd);
                        outputStream.writeInt(8);
                    } else {
                        System.out.println("Error: receive error cmd");
                        //throw new IOException();
                    }
                    /*}else{
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            close();
                            e.printStackTrace();
                        }
                    }*/

                }
            } catch (EOFException e) {
                logger.trace("eof reading from socket; closing", e);
                // connection will be reset so no need to throw an exception.
            } catch (IOException e) {
                logger.debug("IOError reading from socket; closing", e);
            } finally {
                close();
            }
        }
    }

    public void close() {
        if (this.run) {
            this.run = false;
        }
        if (this.socket != null) {
            try {
                this.socket.close();
                socket = null;
            } catch (IOException e) {
                if (logger.isDebugEnabled())
                    e.printStackTrace();
                logger.debug("error closing socket", e);
            }
        }
    }
}
