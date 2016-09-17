package uk.co.riban.esp;

import java.util.Arrays;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortList;

public class MainUIController {
	//FXML element definitions
	@FXML public TabPane tabpaneMain;
	@FXML private ComboBox<String> cmbPort;
	@FXML private ComboBox<Integer> cmbBaud;
	@FXML private Button btnUpload;
	@FXML private ProgressBar progbarUpload;
	@FXML private CheckMenuItem menuDebug;
    @FXML private CheckMenuItem menuAnimate;
    @FXML private VBox vboxFirmwares;
        
	/**	FXML initialisation */
	public void initialize(){
		cmbBaud.getItems().addAll(Main.BAUDS);
		cmbBaud.setValue(115200); //Let's choose a medium speed as default
		
		//Get persistent data
		if(Main.props.getProperty("debug", "false").equals("true")) {
			menuDebug.setSelected(true);
			Main.bDebug = true;
		} else {
			menuDebug.setSelected(false);
			Main.bDebug = false;
		}
		if(Main.props.getProperty("animate", "true").equals("true")) {
    		menuAnimate.setSelected(true);
    		Main.bAnimate = true;
    	} else {
    		menuAnimate.setSelected(false);
    		Main.bAnimate = false;
    	}

		String sUploadPort = Main.props.getProperty("upload_port", "");
		String [] asUploadPort = sUploadPort.split(",");
		if(asUploadPort.length != 2) {
		}
		else {
			cmbPort.setValue(asUploadPort[0]);
			cmbBaud.setValue(Integer.parseInt(asUploadPort[1]));
		}
		
		int nId = 0;
		String sTerm;
		String sKey = String.format("Terminal_%d", nId);
		while((sTerm = Main.props.getProperty(sKey)) != null) {
			String[] asTerm = sTerm.split(",");
			if(asTerm.length != 6) {
				Main.props.remove(sKey);
				break; //Malformed configuration
			}
			TerminalTab terminal = new TerminalTab(asTerm[0]);
			if(!asTerm[1].isEmpty())
				terminal.getController().setPort(asTerm[1]);
			if(!asTerm[2].isEmpty())
				terminal.getController().setBaud(Integer.parseInt(asTerm[2]));
			if(!asTerm[3].isEmpty())
				terminal.getController().setBits(Integer.parseInt(asTerm[3]));
			if(!asTerm[4].isEmpty())
				terminal.getController().setParity(asTerm[4]);
			if(!asTerm[5].isEmpty())
				terminal.getController().setStop(asTerm[5]);
			tabpaneMain.getTabs().add(terminal);
			Main.props.remove(sKey); //Remove the config. We add each terminal config on exit.
			sKey = String.format("Terminal_%d", ++nId);
		}
		refreshPorts();
		nId = 0;
		String sFirmware;
		sKey = String.format("Firmware_%d", nId);
		while((sFirmware = Main.props.getProperty(sKey)) != null) {
			String[] asFirmware = sFirmware.split(",");
			if(asFirmware.length != 3) {
				Main.props.remove(sKey);
				break; //Malformed configuration
			}
			FirmwareConfig fw = new FirmwareConfig();
			fw.setEnabled(asFirmware[2].equals("true"));
			fw.setPath(asFirmware[1]);
			fw.setOffset(asFirmware[0]);
			vboxFirmwares.getChildren().add(fw);
			sKey = String.format("Firmware_%d", ++nId);
		}

	}

	//FXML element event handlers
	@FXML void onMenuQuit(ActionEvent event) {
		Platform.exit();
	}
	
	@FXML void onMenuRefreshSerialPorts(ActionEvent event) {
		refreshPorts();
	}
	
	void refreshPorts() {
		   String[] asPorts = SerialPortList.getPortNames(); 
		   cmbPort.getItems().setAll(asPorts);
			if(cmbPort.getItems().size() == 0)
				cmbPort.setDisable(true);
			else {
				cmbPort.setDisable(false);
		    	if(!Arrays.asList(asPorts).contains(cmbPort.getValue())) {
		    		cmbPort.setValue(asPorts[0]); //Default to first port if invalid selection
		    	}
			}
			for(Tab tab: tabpaneMain.getTabs()) {
			if(tab instanceof TerminalTab ) {
				((TerminalTab) tab).refreshPort();
			}
		}
	}

	@FXML
	void onCmbPortShowing(Event event) {
		refreshPorts();
	}
	
	@FXML void onMenuResetToast(ActionEvent event) {
		Toast.resetQueue();
	}
	
	@FXML void onMenuAbout(ActionEvent event) {
		Toast.show("Jesper is a Java application to control the ESP8266 range of WiFi enabled microcontrollers.\n\n" +
				"Copyright 2016 riban\n" +
				"Author: Brian Walton\n" +
				"jSSC - LGLP Sokolov Alexey\n" +
				"ESP8266 interface inspired by esptool by Fredrik Ahlberg, Angus Gratton et al");
	}
	    
    @FXML void onBtnUploadAction(ActionEvent event) {
    	//Validate serial port still valid, e.g. not USB interface disconnected during runtime
    	String sPort = cmbPort.getValue();
    	if(validatePort(sPort) != sPort) {
    		Toast.show("Serial port is not available");
    		return;
    	}
    	SerialPort serialport = new SerialPort(sPort);

    	suspendTerminals(sPort);
    	//Open serial port
    	try {
    		esp8266 esp = new esp8266(serialport, cmbBaud.getValue());
    		for(Node node: vboxFirmwares.getChildren()) {
    			FirmwareConfig fw = (FirmwareConfig)node;
    			if(fw.isEnabled()) {
    				Main.debug("Uploading firmware: %s to %s", fw.getPath(), fw.getOffset());
    			}
    		}
    		
    		if(serialport.closePort())
    			Main.debug("Closed upload port");
    	}
    	catch (SerialPortException ex) {
    		try {
    			if(serialport.isOpened())
    				serialport.closePort();
			} catch (SerialPortException e) {
				Main.debug("Unable to close port " + serialport.getPortName());
			}
    		Main.debug("Failed to open upload serial port. " + ex.getMessage());
    	}
    	resumeTerminals();
    }
    
    @FXML void onBtnAddFirmwareRow(ActionEvent event) {
    	vboxFirmwares.getChildren().add(new FirmwareConfig());
    }
    
    @FXML void onBtnRemoveFirmwareRow(ActionEvent event) {
    	int nChildren = vboxFirmwares.getChildren().size();
    	if(nChildren > 0)
    		vboxFirmwares.getChildren().remove(nChildren - 1);
    }
    
    @FXML  void onMenuAddTerminal(ActionEvent event) {
    	final TerminalTab tab = new TerminalTab("Terminal");
    	tabpaneMain.getTabs().add(tab);
    	tabpaneMain.getSelectionModel().select(tab);
    }
    
    @FXML
    void onMenuDebug(ActionEvent event) {
 	   Main.bDebug = menuDebug.isSelected();
    }
    
    @FXML
    void onMenuReset(ActionEvent event) {
    	SerialPort serialport = new SerialPort(cmbPort.getValue());
    	suspendTerminals(cmbPort.getValue());
    	try {
    		esp8266 esp = new esp8266(serialport, cmbBaud.getValue());
    		if(!esp.reset(false))
    			Main.debug("Failed to reset ESP8266");
    	} catch(Exception e) {
    		Main.debug("Failed to reset ESP8266");
    	}
    	resumeTerminals();
    }

    @FXML
    void onMenuResetBootloader(ActionEvent event) {
    	SerialPort serialport = new SerialPort(cmbPort.getValue());
    	suspendTerminals(cmbPort.getValue());
    	try {
    		esp8266 esp = new esp8266(serialport, cmbBaud.getValue());
    		if(!esp.reset(true))
    			Main.debug("Failed to reset ESP8266");
    	} catch(Exception e) {
    		Main.debug("Failed to reset ESP8266");
    	}
    	resumeTerminals();
    }
    
    @FXML
    void onMenuTest(ActionEvent event) {
//    	Toast.show("Circinus is a small, faint constellation in the southern sky, first defined in 1756 by French astronomer Nicolas Louis de Lacaille. Its name is Latin for compass, a tool that draws circles. Its brightest star is the slightly variable Alpha Circini, the brightest rapidly oscillating Ap star in the night sky, with an apparent magnitude of 3.19. AX Circini is a Cepheid variable visible with the unaided eye, and BX Circini is a faint star thought to have been formed from two merged white dwarfs.");
  
    	SerialPort serialport = new SerialPort(cmbPort.getValue());
    	try {
    		esp8266 esp = new esp8266(serialport, cmbBaud.getValue());
    		if(esp.connect()) {
    			Main.debug("Connected to ESP8266");
	    		int[] mac = esp.getMac();
	    			Main.debug("MAC: %02x:%02x:%02x:%02x\n", mac[0], mac[1], mac[2],mac[3]);
    		}
    		serialport.closePort();
    	}
    	catch (SerialPortException ex) {
    		try {
    			if(serialport.isOpened())
    				serialport.closePort();
			} catch (SerialPortException e) {
				Main.debug("Unable to close port " + serialport.getPortName());
			}
    		Main.debug("Failed to open upload serial port. " + ex.getMessage());
    	} catch (Exception e) {
    		Main.debug("Failed to get MAC");
    	}

    }
    
    @FXML
    void onMenuAnimate(ActionEvent event) {
    	Main.bAnimate = menuAnimate.isSelected();
    }
    
    void saveSettings() {
		if(cmbPort.getValue() == null)
			Main.props.remove("upload_port");
		else
			Main.props.setProperty("upload_port", String.format("%s,%s", cmbPort.getValue(), cmbBaud.getValue()));

		for(int nIndex = 0; nIndex < vboxFirmwares.getChildren().size(); ++nIndex) {
			FirmwareConfig fw = (FirmwareConfig)(vboxFirmwares.getChildren().get(nIndex));
			Main.props.setProperty(String.format("Firmware_%d", nIndex), String.format("%s,%s,%s",
					(fw.getOffset() == null)?"":fw.getOffset(),
					fw.getPath(),
					fw.isEnabled()?"true":"false"));
		}
	}
    
    String validatePort(String sPort) {
    	String[] asPorts = SerialPortList.getPortNames(); 
    	if(Arrays.asList(asPorts).contains(sPort))
    		return sPort;
    	else if (asPorts.length > 0)
    		return asPorts[0];
    	else
    		return "";
    }
    
    private void suspendTerminals(String sPort) {
		//Suspend terminal using this port
		for(Tab tab: tabpaneMain.getTabs()) {
			if(tab instanceof TerminalTab ) {
				if (!((TerminalTab) tab).suspendPort(sPort)) {
					Main.debug("Failed to suspend port for terminal " + tab.getText());
					return;
				}
			}
		}
    }
    
	private void resumeTerminals() {
    	//Resume terminal using this port
		for(Tab tab: tabpaneMain.getTabs()) {
			if(tab instanceof TerminalTab) {
				((TerminalTab) tab).resumePort();
			}
		}
	}
}