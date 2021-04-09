/*
 * Copyright (c) 2010-2020 Nathan Rajlich
 *
 *  Permission is hereby granted, free of charge, to any person
 *  obtaining a copy of this software and associated documentation
 *  files (the "Software"), to deal in the Software without
 *  restriction, including without limitation the rights to use,
 *  copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following
 *  conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 *  OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 *  HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 *  OTHER DEALINGS IN THE SOFTWARE.
 */

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.log4j.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;

public class ServerStressTest extends JFrame {
	
	private Logger log = Logger.getLogger(ServerStressTest.class);

	class SDPWebsocketClient extends WebSocketClient {

		int id;
		private Timer heartBeatTimer = new Timer(true);

		public SDPWebsocketClient(URI serverUri, int id) {
			super(serverUri);
			this.id = id;
		}

		@Override
		public void onClose(int code, String reason, boolean remote) {
			log.debug("Connection closed by " + (remote ? "remote peer" : "us") + " Code: " + code
					+ " Reason: " + reason);
			websockets.remove(id);
			connectedClients.remove(id);
			clients.setValue(websockets.size());			
		}

		@Override
		public void onError(Exception ex) {
			ex.printStackTrace();
		}

		@Override
		public void onMessage(String message) {
			//log.debug("for id:"+id+" "+ connectedClients.add(id));
			if(message.contains("FRONT_END_UI_INITIAL_STATE")) {
				connectedClients.add(id);
			}			
			log.debug("received message for client:"+id+" : " + message);
		}

		@Override
		public void onOpen(ServerHandshake arg0) {
			log.debug("opened connection for client : "+id);
			
			websockets.put(id, this);
			clients.setValue(websockets.size());
			
			String nodeRegMessage = "{\"NODE_REGISTRATION\":{\"authToken\":\"loadtest" + id
					+ "\",\"username\":\"loadtest" + id + "\"}}";
			send(nodeRegMessage);
			log.debug("Sent Node Registration for client:"+id);
			heartBeatTimer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					clientHeartBeat();
				}
			}, 0, interval.getValue());
		}

		public void clientHeartBeat() {
			try {
				send("{\"NODE_HEARTBEAT\":{\"authToken\":\"loadtest" + id + "\",\"serverId\":\"90" + id + "\"}}");
				log.debug("Sent heartbeat for client:"+id);
			} catch (WebsocketNotConnectedException e) {
			}
		}
	}

	private JSlider clients;
	private JSlider interval;
	private JSlider joinrate;
	private JButton start, stop, reset;
	private JLabel joinratelabel = new JLabel();
	private JLabel clientslabel = new JLabel();
	private JLabel intervallabel = new JLabel();
	private JTextField uriinput = new JTextField("wss://fxsdp.uat.ntrs.com/sdpws");
	private Timer timer = new Timer(true);
	private Thread adjustthread;

	public ServerStressTest() {
		setTitle("ServerStressTest");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		start = new JButton("Start");
		start.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				start.setEnabled(false);
				stop.setEnabled(true);
				reset.setEnabled(false);
				interval.setEnabled(false);
				clients.setEnabled(false);

				stopAdjust();
				adjustthread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							adjust();
						} catch (InterruptedException e) {
							log.debug("adjust chanced");
						}
					}
				});
				adjustthread.start();

			}
		});
		stop = new JButton("Stop");
		stop.setEnabled(false);
		stop.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				timer.cancel();
				stopAdjust();
				start.setEnabled(true);
				stop.setEnabled(false);
				reset.setEnabled(true);
				joinrate.setEnabled(true);
				interval.setEnabled(true);
				clients.setEnabled(true);
			}
		});
		reset = new JButton("reset");
		reset.setEnabled(true);
		reset.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				while (!websockets.isEmpty()) {
					websockets.remove(Collections.max(websockets.keySet())).close();
				}
			}
		});
		joinrate = new JSlider(0, 5000);
		joinrate.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				joinratelabel.setText("Joinrate: " + joinrate.getValue() + " ms ");
			}
		});
		clients = new JSlider(0, 100);
		clients.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				clientslabel.setText("Clients: " + clients.getValue());

			}
		});
		interval = new JSlider(0, 5000);
		interval.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				intervallabel.setText("Interval: " + interval.getValue() + " ms ");

			}
		});

		setSize(300, 400);
		setLayout(new GridLayout(10, 1, 10, 10));
		add(new JLabel("URI"));
		add(uriinput);
		add(joinratelabel);
		add(joinrate);
		add(clientslabel);
		add(clients);
		add(intervallabel);
		add(interval);
		JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
		add(south);

		south.add(start);
		south.add(stop);
		south.add(reset);

		joinrate.setValue(200);
		interval.setValue(1000);
		clients.setValue(1);

	}

	ConcurrentHashMap<Integer, WebSocketClient> websockets = new ConcurrentHashMap<Integer, WebSocketClient>();
	Set<Integer> connectedClients = new HashSet<Integer>();
	int counter = 1;
	URI uri;

	public void adjust() throws InterruptedException {
		log.debug("Adjust");
		try {
			uri = new URI(uriinput.getText());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		int totalclients = clients.getValue();
		int initiated = websockets.size();
		while (initiated < totalclients) {
			WebSocketClient client = new SDPWebsocketClient(uri, counter);
			client.connect();

			Thread.sleep(joinrate.getValue());
			counter++;
			initiated++;
		}

		while (websockets.size() > clients.getValue()) {
			websockets.remove(Collections.max(websockets.keySet())).close();
		}

		timer = new Timer(true);
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				log.debug("Clients Connected:" + connectedClients.size());
			}
		}, 0, interval.getValue());
	}

	public void stopAdjust() {
		if (adjustthread != null) {
			adjustthread.interrupt();
			try {
				adjustthread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new ServerStressTest().setVisible(true);
	}

}
