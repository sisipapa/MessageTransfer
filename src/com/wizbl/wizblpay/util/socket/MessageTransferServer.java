package com.wizbl.wizblpay.util.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageTransferServer {
	
	private static Logger logger = LoggerFactory.getLogger(MessageTransferServer.class);
	
	private final int port = 9999;
	private final int paytimeoutmin = 5;
	private final int schedulesec = 10;
    private Selector selector = null;
    private ServerSocketChannel serverSocketChannel = null;
    private ServerSocket serverSocket = null;
    private boolean isStart = true;

    private List<Hashtable<String, Object>> rList = new ArrayList<Hashtable<String, Object>>();
    
    public MessageTransferServer() {
    	initServer();
//    	checkPayList();
    }
    
    /**
     * 
     * <pre>
     * SocketChannel 서버 초기화
     * </pre>
     * 
     * @Method Name : initServer
     * @author : khkim
     * @since : 2019. 1. 8.
     *
     */
    public void initServer() {
        try {
            selector = Selector.open();

            // Create ServerSocket Channel
            serverSocketChannel = ServerSocketChannel.open();

            // 비 블록킹 모드로 설정한다.
            serverSocketChannel.configureBlocking(false);

            // 서버소켓 채널과 연결된 서버소켓을 가져다.
            serverSocket = serverSocketChannel.socket();

            // 주어진 파라미터에 해당는 주소다. 포트로 서버소켓을 바인드한다.
            InetSocketAddress isa = new InetSocketAddress(port);
            serverSocket.bind(isa);

            // 서버소켓 채널을 셀렉터에 등록한다.
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            
            logger.debug("MessageTransferServer init");
            
        } catch (IOException ex) {
        	logger.error("initServer ex : " + ex.getMessage(), ex);
            ex.printStackTrace();
        }
    }
    
    /**
     * 
     * <pre>
     * ServerSocketChannel 응답대기
     * </pre>
     * 
     * @Method Name : startServer
     * @author : khkim
     * @since : 2019. 1. 8.
     *
     */
    public void startServer() {
        try {
        	
            while (isStart) {
                selector.select(); // 셀렉터 select() 메소드로 준비된 이벤트가 있는지 확인한다.

                Iterator it = selector.selectedKeys().iterator();

                while (it.hasNext()) {
                    SelectionKey key = (SelectionKey) it.next();

                    if (key.isValid() && key.isAcceptable()) {
                        accept(key);
                    } else if (key.isValid() && key.isReadable()) {
                        read(key);
                    }
                    it.remove();
                } // end of while(iterator)
            }
        } catch (Exception ex) {
            logger.error("startServer ex : " + ex.getMessage(), ex);
        	ex.printStackTrace();
        }
    }
    
    /**
     * 
     * <pre>
     * ServerSocketChannel 요청 accept
     * </pre>
     * 
     * @Method Name : accept
     * @author : khkim
     * @since : 2019. 1. 8.
     * 
     * @param key
     */
    private void accept(SelectionKey key) {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();

        try {
            // 서버소켓 채널 accept() 메소드로 서버소켓을 생성한다.
            SocketChannel sc = server.accept();
            // 생성된 소켓채널을 비 블록킹과 읽기 모드로 셀렉터에 등록한다.

            if (sc == null)
                return;

            sc.configureBlocking(false);
            sc.register(selector, SelectionKey.OP_READ);

//            room.add(sc); // 접속자 추가
            logger.debug(sc.toString() + " 결제요청");
        } catch (Exception ex) {
        	logger.error("accept : " + ex.getMessage(), ex);
            ex.printStackTrace();
        }
    }
    
    /**
     * 
     * <pre>
     * Send, Receive 요청 내용 read
     * </pre>
     * 
     * @Method Name : read
     * @author : khkim
     * @since : 2018. 12. 20.
     * 
     * @param key
     */
    private void read(SelectionKey key) {

        // SelectionKey로부터 소켓채널을 얻어다.
        SocketChannel sc = (SocketChannel) key.channel();
        // ByteBuffer를 생성한다.
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        
        Hashtable<String, Object> pay = new Hashtable<String, Object>();
        String message = "";
        
        try {
            // 요청한 클라이언트 소켓채널로부터 데이터를 읽어들인다.
        	if(sc != null) {
        		sc.read(buffer);
        	}
            buffer.flip();
//			byte[] array = new byte[buffer.limit()];
//			buffer.get(array);
//			message = new String(array);
            message = Charset.forName("UTF-8").decode(buffer).toString();
//			logger.debug("message : " + message);
			
			// 보내는 사람 - 타입 + "KEY" + 거래키(발신자번호 + 수신자번호 + 현재시간 Millisec + "MESSAGE" + 메세지)
			// 받는 사람 - 타입 + "KEY" + 거래키(발신자번호 + 수신자번호 + 현재시간 Millisec)
			// ex) 보내는 사랑 - SKEY01000000001010000000021545114168321MESSAGE결제금액은_1,500RPY_입니다.AAAbbDD
			// ex)  받는 사람 - RKEY01000000001010000000021545114168321
            if(message != null && !message.equals("")) {
            	
            	logger.debug("message :" + message);
            	
            	if(message.startsWith("R")) {  // RECEIVER의 경우만 rList에 저장
            		
            		pay.put("MESSAGE", message);
            		pay.put("SC",sc);
            		pay.put("DATE", System.currentTimeMillis());
            		
            		logger.debug(message);
            		
            		// ArrayList의 contain 메소드를 사용했으나 DATE 값이 항상 달라 지기 때문에 개별체크로 변경
//            		if(!rList.contains(pay)) {	// 동일한 MESSAGE와 동일한 SOCKETCHANNEL 요청이 아닐 경우에만 rList에 add
//            			rList.add(pay);
//            		}
            		
            		int size = rList.size();
            		
            		if(size != 0) {
            			
            			//boolean isRegister = false;
            			int index = -1;
            			for(int i=0; i<size; i++) {
            				
            				Hashtable<String, Object> lpay = rList.get(i);
            				String lmessage = (String)lpay.get("MESSAGE");
        					if(message.equals(lmessage)) {
        						index = i;
            				}
            				
            			}
            			
            			if(index != -1) {
            				// 기존 요청 rList삭제, SocketChannel close
    						Hashtable<String, Object> lpay = rList.get(index);
    						SocketChannel lsc = (SocketChannel)lpay.get("SC");
    						logger.debug(lpay.toString() + "R Connection 삭제");
    						lsc.close();
    						rList.remove(index);
            			}
            			
            			// 신규 요청 rList등록
            			rList.add(pay);
            			logger.debug(pay.toString() + "R Connection 등록");
            			
            		}else {
            			rList.add(pay);
            		}
            		
            		logger.debug(rList.toString());
             		logger.debug("결제 Receive Connection (read) : " + rList.size() + "건");
            	}
            	
            }
            
        } catch (IOException ex) {
        	
        	logger.error("read ex : " + ex.getMessage(), ex);
        	ex.printStackTrace();
            try {
                sc.close();
            } catch (IOException e) {
            	logger.error("read e : " + e.getMessage(), e);
            	e.printStackTrace();
            }

            rList.remove(pay);
        }
        
        try {
        	if(message.startsWith("S")) {  // SENDER가 보내는 요청일 경우만 broadcast
        		broadcast(message, sc);
        	}
        } catch (IOException ex) {
        	ex.printStackTrace();
        	logger.error("broadcast ex : " + ex.getMessage(), ex);
        } 
        
        if (buffer != null) {
            buffer.clear();
            buffer = null;
        }
    }
    
    /**
     * 
     * <pre>
     * Send 메세지 Client SocketChannel write
     * </pre>
     * 
     * @Method Name : broadcast
     * @author : khkim
     * @since : 2018. 12. 20.
     * 
     * @param senderMessage
     * @throws IOException
     */
    private synchronized void broadcast(String senderMessage, SocketChannel ssc) throws IOException {
    	
    	String payKey = senderMessage.substring(1, senderMessage.indexOf("MESSAGE"));
    	String payMessage = senderMessage.substring(senderMessage.indexOf("MESSAGE") + "MESSAGE".length());
    	
    	logger.debug("payMessage : " + payMessage);
    	logger.debug("payKey : " + payKey);
    	
    	Iterator<Hashtable<String, Object>> iter = rList.iterator();
    	Hashtable<String, Object> pay = null;
    	
    	int count = 0;
        while (iter.hasNext()) // 결제대기중인
        {
        	pay = (Hashtable<String, Object>) iter.next();
        	String tableMessage = (String)pay.get("MESSAGE");
        	String tPayKey = tableMessage.substring(1);
        	
        	logger.debug("tPayKey : " + tPayKey);
        	
        	if(tPayKey.equals(payKey)) {
        		
        		SocketChannel sc = (SocketChannel)pay.get("SC");
        		if (sc != null) {
        			
        			logger.debug("client에 전송할 메세지 ==================> " + payMessage);
        			sc.write(Charset.forName("UTF-8").encode(payMessage));
        			count ++;
        			
        		}
        	}
        }
        
//        if(isSend) {
        if(count != 0) {
//        	rList.remove(pay);
        	ssc.write(Charset.forName("UTF-8").encode("SENDSUCCESS"));
        	logger.debug("SENDSUCCESS");
        	ssc.close();		// 전송 후 SEND SocketChannel close ==> close를 하면 client에서 무한루프..
        }
        
//        logger.debug("결제 Send Connection (broadcast) : " + sList.size() + "건");
        logger.debug("결제 Receive Connection (broadcast) : " + rList.size() + "건");
    }
    
    /**
     * 
     * <pre>
     * 주기적으로 rList의 요청시간을 체크해서 일정시간이 지난 결제 요청 삭제
     * </pre>
     * 
     * @Method Name : checkPayList
     * @author : khkim
     * @since : 2018. 12. 20.
     *
     */
    public void checkPayList() {
    	
    	TimerTask task = new TimerTask() {
			@Override
			public void run() {
				logger.debug("PAYLIST SCHEDULING...");
				// task to run goes here
				Iterator<Hashtable<String, Object>> iter = rList.iterator();
				int listCount = 0;
				while(iter.hasNext()) {
					listCount++;
					logger.debug("[" + listCount + "번째]결제 대기중인 목록 시간체크");
					
					Hashtable<String, Object> table = iter.next();
					long payMilliSec = (Long)table.get("DATE");
					
					payMilliSec = payMilliSec + (paytimeoutmin * 60 * 1000);
					long currMilliSec = System.currentTimeMillis();
					
					if(payMilliSec < currMilliSec) {
						
						String message = (String)table.get("MESSAGE");
						SocketChannel sc = (SocketChannel)table.get("SC");
						iter.remove();	// 응답 List 상에서 메세지 삭제
						String deleteMessage = message + " 결제 정보가 결제 대기시간 " + paytimeoutmin + "분을 초과하여 결제 대기열에서 삭제되었습니다.";
						logger.debug(deleteMessage);
						logger.debug("결제 요청 대기건(checkPayList) : " + rList.size() + "건");
						try {
							sc.write(Charset.forName("UTF-8").encode(deleteMessage));
						} catch (IOException e) {
							// TODO Auto-generated catch block
							try {
								sc.close();
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
							e.printStackTrace();
						}
						
					}
					
				}
				
			}
		};

		Timer timer = new Timer();
		long delay = 1000;
		long intevalPeriod = schedulesec * 1000;
		// schedules the task to be run in an interval
		timer.scheduleAtFixedRate(task, delay, intevalPeriod);
		
    }

    /**
     * 
     * <pre>
     * String을 ByteBuffer로
     * </pre>
     * 
     * @Method Name : str_to_bb
     * @author : khkim
     * @since : 2019. 1. 8.
     * 
     * @param msg
     * @param charset
     * @return
     */
    public static ByteBuffer str_to_bb(String msg, Charset charset){
        return ByteBuffer.wrap(msg.getBytes(charset));
    }

   /**
    * 
    * <pre>
    * ByteBuffer를 String으로
    * </pre>
    * 
    * @Method Name : bb_to_str
    * @author : khkim
    * @since : 2019. 1. 8.
    * 
    * @param buff
    * @param charset
    * @return
    */
    public static String bb_to_str(ByteBuffer buff, Charset charset){
      byte[] bytes;
      if (buff.hasArray()) {
        bytes = buff.array();
      } else {
        bytes = new byte[buff.remaining()];
        buff.get(bytes);
      }
      return new String(bytes);
    }
    
    public static void main(String[] args) {
    	logger.debug("MessageTransferServer main start...");
    	new MessageTransferServer().startServer();
    	logger.debug("MessageTransferServer main end...");
    }
}