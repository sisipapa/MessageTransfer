package com.wizbl.wizblpay.util.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageTransferClient {
	
	private static Logger logger = LoggerFactory.getLogger(MessageTransferServer.class);
	
//	private final String ip = "127.0.0.1";	//local
	private final String ip = "13.209.58.224";	//aws
//	private final String ip = "121.131.72.185";	//igo
	private final int port = 9999;
    static Selector selector = null;
    private SocketChannel sc = null;
    
    public MessageTransferClient() {}

    /**
     * 
     * <pre>
     * 클라이언트 SocketChannel open
     * </pre>
     * 
     * @Method Name : initServer
     * @author : khkim
     * @since : 2018. 12. 19.
     *
     */
    public void initServer() {
        try {
            selector = Selector.open();
            sc = SocketChannel.open(new InetSocketAddress(ip, port));
            sc.configureBlocking(false);
            sc.register(selector, SelectionKey.OP_READ);
        } catch (IOException ex) {
        	ex.printStackTrace();
        }
    }
    
    /**
     * 
     * <pre>
     * 클라이언트 SocketChannel 시작
     * </pre>
     * 
     * @Method Name : startServer
     * @author : khkim
     * @since : 2018. 12. 19.
     *
     */
    public void startServer() {
    	initServer();
        Receive rt = new Receive();
        new Thread(rt).start();
    }
    
    /**
     * 
     * <pre>
     * 결제요청(결제키 오늘날짜 사용)
     * </pre>
     * 
     * @Method Name : transferPayMessage
     * @author : khkim
     * @since : 2018. 12. 19.
     * 
     * @param type
     * @param from
     * @param to
     * @param message
     */
    public void transferPayMessage(String type, String key, String message) {
    	
    	// SocketChannel 시작
    	startServer();
    	
    	
    	ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
    	try {
    		
    		String data = type + key;
    		if(type.equals("S")) {
    			data = data + "MESSAGE" + message;
    		}
    		buffer.clear();
    		buffer.put(data.getBytes());
    		buffer.flip();
    		sc.write(buffer);
    		
    	} catch (Exception ex) {
    		ex.printStackTrace();
    	} finally {
    		clearBuffer(buffer);
    	}
    }
    
    static void clearBuffer(ByteBuffer buffer) {
        if (buffer != null) {
            buffer.clear();
            buffer = null;
        }
    }
    
    public static void main(String[] args) {
    	MessageTransferClient client = new MessageTransferClient();
    	
//    	client.closeConnection("S");	
    	for(int i=0; i<100; i++) {
//    		client.transferPayMessage("R", "01011111113", "");	//RECEIVE의 경우 3번째 파라미터 빈값
//    		if(i < 10) {
//    			client.transferPayMessage("R", "0102222220"+i, "");	//RECEIVE의 경우 3번째 파라미터 빈값
//    		}else {
//    			client.transferPayMessage("R", "010222222"+i, "");	//RECEIVE의 경우 3번째 파라미터 빈값
//    		}
    		
    	}
    	client.transferPayMessage("R", "01011111111", "");	//RECEIVE의 경우 3번째 파라미터 빈값
//    	client.transferPayMessage("R", "01022222222", "");	//RECEIVE의 경우 3번째 파라미터 빈값
//		client.transferPayMessage("S", "01011111111", "보내고자하는메세지1");	//RECEIVE의 경우 3번째 파라미터 빈값
//		client.transferPayMessage("S", "01022222222", "보내고자하는메세지2");	//RECEIVE의 경우 3번째 파라미터 빈값
    }
}

class Receive implements Runnable {
	
	private static Logger logger = LoggerFactory.getLogger(Receive.class);
	
    private Charset charset = null;
    private CharsetDecoder decoder = null;
    private boolean isWait = true;

    public void run() {
        charset = Charset.forName("UTF-8");
        decoder = charset.newDecoder();
        try {
            while (isWait) 
            {	
                MessageTransferClient.selector.select();
                Iterator it = MessageTransferClient.selector.selectedKeys().iterator();
                
                while (it.hasNext()) 
                {
                    SelectionKey key = (SelectionKey) it.next();
                    if(key.isReadable()) {
                        read(key);
                    }else{}
                    it.remove();
                }
            }
        } catch (Exception ex) {}
    }

    private void read(SelectionKey key) {
    	
        SocketChannel sc = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        int nbyte=0;
        try {
        	
        	buffer.position();
            nbyte = sc.read(buffer);
            logger.debug("" + nbyte);
            if(nbyte == -1) {
            	
            	isWait = false;
            	logger.debug("SocketChannel read END");
            	
            }else {
            	
            	buffer.flip();
            	String message = decoder.decode(buffer).toString();
            	returnMessage(message, sc);
            	MessageTransferClient.clearBuffer(buffer);
            }
            
        }catch (IOException ex){
            try {
                sc.close();
            } catch (IOException ex1) {
            	ex1.printStackTrace();
            }
        }
    }
    
    private String returnMessage(String message, SocketChannel sc) {
    	logger.debug("message : " + message);
//    	isWait = false;
//    	try {
//    		sc.close();
//		} catch (IOException ie) {
//			ie.printStackTrace();
//		}
    	return message;
    }
    
}