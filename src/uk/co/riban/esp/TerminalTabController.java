package uk.co.riban.esp;

import java.util.Arrays;

import javafx.animation.Animation;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.util.Duration;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortList;

public class TerminalTabController {

	private SerialPort serialport = null;
	
	//Define FXML components
		@FXML private Node root;
	    @FXML private ComboBox<String> cmbPort;
	    @FXML private Button btnConnect;
	    @FXML private ComboBox<Integer> cmbBaud;
	    @FXML private TextArea txtConsole;
	    @FXML private ComboBox<Integer> cmbBits;
	    @FXML private ComboBox<String> cmbParity;
	    @FXML private ComboBox<String> cmbStop;
	    @FXML private GridPane gridSettings;
	    @FXML private Label lblDetails;
	    	    
	    /** FXML initialisation */
	    public void initialize() {
			String[] asPorts = SerialPortList.getPortNames();
			cmbPort.getItems().setAll(asPorts);			
			cmbBaud.getItems().addAll(Main.BAUDS);
			cmbBits.getItems().addAll(Main.WORD_LENGTHS);
			cmbParity.getItems().addAll(Main.PARITY_VALUES);
			cmbStop.getItems().addAll(Main.STOP_BIT_VALUES);

			cmbBaud.setValue(9600);
			cmbBits.setValue(8);
			cmbParity.setValue("none");
			cmbStop.setValue("1");
			refreshPort();
	    }

	    @FXML
	    void onBtnConnectAction(ActionEvent event) {
	    	if(serialport != null && serialport.isOpened()) {
	    		closePort(false);
	    		showSettings(true);
	    	} else {
	    		openPort();
	    		showSettings(false);
	    	}
	    }
	    
	    @FXML
	    void onBtnClear(ActionEvent event) {
	    	txtConsole.clear();
	    	setFocus();
	    }

	    @FXML
		void onCmbPortShowing(Event event) {
			Main.controller.refreshPorts();
		}

	    void showSettings(boolean  bShow) {
	    	if(!Main.bAnimate) {
	    		gridSettings.setPrefWidth(bShow?200:0);
	    		return;
	    	}

	    	final Animation aniSlide = new Transition() {
	    		{
	    			setCycleDuration(Duration.millis(250));
	    		}
	    		
	    	protected void interpolate(double frac) {
	              final double curWidth = 200 * frac;
	              gridSettings.setPrefWidth(curWidth);
	              gridSettings.setTranslateX(-200 + curWidth);
	    		}
	    	};
	    	
	    	if(bShow) {
	    		aniSlide.setRate(1);
		    	aniSlide.play();
	    	} else {
	    		aniSlide.setRate(-1);
		    	aniSlide.setOnFinished(new EventHandler<ActionEvent>() {
		    		@Override
					public void handle(ActionEvent event) {
		    		}
		    	});
	    		aniSlide.playFrom(aniSlide.getCycleDuration());
	    	}
	    }
	   
	    public SerialPort GetSerialPort() {
	    	return serialport;
	    }
	    

		/**
		 * @brief	Requests the terminal suspends serial port
		 * @param	port Name of port to suspend
		 * @return	boolean True on success
		 * @note	Does nothing if port not open or wrong port name
		 */
		public boolean suspendPort(final String port) {
			if(serialport == null || !serialport.getPortName().equals(port))
			{
				return true;
			}
			return closePort(true);
		}
		
		/**
		 * Resumes suspended serial port
		 * @return	void
		 * @note	Does nothing if port not suspended
		 */
		public void resumePort() {
			if(serialport == null)
				return;
			openPort();
		}
		
		/**
		 * Closes serial port if open
		 * @param bSuspend True to mark port as suspended so that it will be resumed on request
		 * @return True on success
		 */
		boolean closePort(final boolean bSuspend) {
			if(serialport == null)
				return true;
			if(serialport.isOpened())
				try {
					serialport.closePort();
					Main.debug("Closed terminal port");
				} catch (Exception e) {
					Main.debug("Failed to close terminal port. " + e.getMessage());
					Toast.show("Failed to close terminal serial port");
//!@todo Why can't we close port?					return false;
				}
			txtConsole.setDisable(true);
			btnConnect.setText("Connect");
			if(!bSuspend) {
				serialport = null;
			}
			lblDetails.setText("");
			return true;
		}
		
		private boolean openPort() {
			if(serialport != null) {
				//Port already assigned. Let's close it first
				if(!closePort(false))
					return false;
				serialport = null;
			}
			serialport = new SerialPort(cmbPort.getValue());
			int nMask = SerialPort.MASK_RXCHAR;
			nMask |= SerialPort.MASK_RXCHAR;
			nMask |= SerialPort.MASK_BREAK;
			nMask |= SerialPort.MASK_CTS;
			nMask |= SerialPort.MASK_DSR;
			nMask |= SerialPort.MASK_RING;
			nMask |= SerialPort.MASK_RLSD;
			nMask |= SerialPort.MASK_RXFLAG;
			try {
				serialport.openPort();
				serialport.setParams(cmbBaud.getValue(), cmbBits.getValue(), ConvertStop(cmbStop.getValue()), ConvertParity(cmbParity.getValue()));
				serialport.setEventsMask(nMask);
			} catch (Exception e) {
				serialport = null;
				Main.debug("Failed to open terminal serial port. " + e.getMessage());
				Toast.show("Failed to open terminal serial port");
				return false;
			}
			txtConsole.setDisable(false);
			Main.debug("Opened terminal port " + serialport.getPortName());
			try {
				serialport.addEventListener(new SerialPortEventListener() {
					@Override
					public void serialEvent(SerialPortEvent event) {
						if(event.isRXCHAR() && event.getEventValue() > 0){
				            try {
				            	int nCount = event.getEventValue();
				            	for(int nIndex = 0; nIndex < nCount; ++nIndex) {
				            		String sChar = serialport.readString(1);
				            		switch(sChar.toCharArray()[0]) {
				            			case 13:
				            				txtConsole.appendText("\n");
				            				break;
				            			default:
				            				txtConsole.appendText(sChar);
				            		}
				            	}
				            } catch (SerialPortException e) {
				            	Main.debug("Error recieving terminal data");
				            }
						} else {
							//!@todo handle removal of serial port
							Main.debug("Serial port event: %d", event.getEventType());
						}
					}
				});
			} catch (SerialPortException e) {
				Main.debug("Failed to create terminal serial port listener");
			}
			setFocus();
			lblDetails.setText(String.format("%s: %d %d-%s-%s", serialport.getPortName(), cmbBaud.getValue(), cmbBits.getValue(), cmbParity.getValue(), cmbStop.getValue()));
			btnConnect.setText("Disconnect");
			return true;
		}

		@FXML
		void onKeyPressed(KeyEvent event) {
			try {
				switch(event.getCode()) {
					case TAB:
							serialport.writeInt(0x09);
						event.consume();
						break;
					//!@todo handle any other non-printable characters
					default:
				}
			} catch(SerialPortException e) {
				//Port probably closed! Don't worry about it.
			}
		}
		
	   @FXML
	   void onKeyTyped(KeyEvent event) {
		   try {
			   serialport.writeString(event.getCharacter());
		   } catch (SerialPortException e) {
			   Main.debug("Error writing to terminal port");
		   }
	   }
	   
	   /**
	    * 	Sets input focus to appropriate control.
	    *	If serial port is connected then focus is set to the text area.
	    * 	If serial port is not connected then focus is set to the connect button.
	    */
	   void setFocus() {
		   Platform.runLater(new Runnable() {			
			@Override
			public void run() {
			   if(serialport == null)
				   btnConnect.requestFocus();
			   else
				   txtConsole.requestFocus();
			}
		});
	   }
	   
	   void refreshPort() {
		   String[] asPorts = SerialPortList.getPortNames(); 
		   cmbPort.getItems().setAll(asPorts);
		   if(cmbPort.getItems().size() == 0) {
			   //There are no serial ports detected
			   closePort(false);
			   showSettings(true);

			   btnConnect.setDisable(true);
		   } else if(Arrays.asList(asPorts).contains(cmbPort.getValue())) {
			   //Currently selected port exists so do nothing
			   return;
		   } else {
			   //Currently selected port no longer exists
			   closePort(false);
			   showSettings(true);
			   cmbPort.setValue(asPorts[0]);
			   btnConnect.setDisable(false);
		   }
	   }

	   private int ConvertStop(String sStop) {
		   if(sStop.toLowerCase() == "1.5")
			   return SerialPort.STOPBITS_1_5;
		   if(sStop.toLowerCase() == "2")
			   return SerialPort.STOPBITS_2;
		   return SerialPort.STOPBITS_1;
	   }
	   
	   private int ConvertParity(String sParity) {
		   if(sParity.toLowerCase() == "odd")
			   return SerialPort.PARITY_ODD;
		   if(sParity.toLowerCase() == "even")
			   return SerialPort.PARITY_EVEN;
		   if(sParity.toLowerCase() == "mark")
			   return SerialPort.PARITY_MARK;
		   if(sParity.toLowerCase() == "space")
			   return SerialPort.PARITY_SPACE;
		   return SerialPort.PARITY_NONE;
	   }
	   
	   String getPort() {
		   return cmbPort.getValue();
	   }
	   
	   Integer getBaud() {
		   return cmbBaud.getValue();
	   }
	   
	   Integer getBits() {
		   return cmbBits.getValue();
	   }
	   
	   String GetParity() {
		   return cmbParity.getValue();
	   }
	   
	   String getStop() {
		   return cmbStop.getValue();
	   }
	   
	   void setPort(String port) {
		   cmbPort.setValue(port);
	   }
	   
	   void setBaud(Integer baud) {
		   cmbBaud.setValue(baud);
	   }
	   
	   
	   void setBits(Integer bits) {
		   cmbBits.setValue(bits);
	   }
	   
	   void setParity(String parity) {
		   cmbParity.setValue(parity);
	   }
	   
	   void setStop(String stop) {
		   cmbStop.setValue(stop);
	   }
	   
	}
