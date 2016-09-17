package uk.co.riban.esp;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.stage.Stage;


public class Main extends Application {
	static public final Integer[] BAUDS = {1200, 2400, 4800, 9600, 19200, 38400, 57600, 74880,  115200, 128000};
    final static Integer[] WORD_LENGTHS = {5, 6, 7, 8};
    final static String[] PARITY_VALUES = {"none", "odd", "even", "mark", "space"};
    final static String[] STOP_BIT_VALUES = {"1", "1.5", "2"};

	static public final String[] ADDRESSES = {"0x00000", "0x01000", "0x10000", "01FC000"};
	static boolean bDebug = false;
    static Stage pStage = null;
    static Scene pScene = null;
    static String sCss;
    static Properties props = null;
    static MainUIController controller = null;
    static boolean bAnimate = true;

	@Override
	public void start(Stage primaryStage) {
		//Load preferences from local configuration file
	    props = new Properties();
	    try {
	    	FileInputStream in = new FileInputStream("jesper.cfg");
	    	props.load(in);
	    	in.close();
	    } catch (Exception e) {
	    	Main.debug("Unable to load configuration file");
	    }
		try {
			pStage = primaryStage;
			final FXMLLoader loader = new FXMLLoader(getClass().getResource("MainUI.fxml"));
			Parent root = loader.load();
			controller = (MainUIController)loader.getController();
			Scene pScene = new Scene(root);
			sCss = getClass().getResource("application.css").toExternalForm();
			pScene.getStylesheets().add(sCss);
			primaryStage.setScene(pScene);
			String[] saLayout = props.getProperty("layout", "10,10,300,400").split(",");
			primaryStage.setWidth(Integer.parseInt(saLayout[2]));
			primaryStage.setHeight(Integer.parseInt(saLayout[3]));
			primaryStage.setX(Integer.parseInt(saLayout[0]));
			primaryStage.setY(Integer.parseInt(saLayout[1]));
			primaryStage.show();
		} catch(Exception e) {
			e.printStackTrace();
		}
		Toast.setStage(pStage);
	}
	
	@Override
	public void stop() {
		
		//Write configuration
		int nTab = 0;
		
		for(Tab tab: controller.tabpaneMain.getTabs()) {
			if(tab instanceof TerminalTab ) {
				((TerminalTab) tab).saveConfig(nTab++);
				((TerminalTab) tab).closePort();
			}
		}
		props.setProperty("debug", Main.bDebug?"true":"false");
    	props.setProperty("animate", Main.bAnimate?"true":"false");
    	if(pStage != null)
    		props.setProperty("layout", String.format("%d,%d,%d,%d", (int)pStage.getX(), (int)pStage.getY(), (int)pStage.getWidth(), (int)pStage.getHeight()));
    	controller.saveSettings();
	    try {
	    	FileOutputStream out = new FileOutputStream("jesper.cfg");
	    	Main.props.store(out, "Jesper configuration - overwritten when jesper closes");
	    	out.close();
	    } catch (Exception e) {
	    	Main.debug("Unable to save configuration file: " + e.getMessage());
	    }
	}
	
	public static void main(String[] args) {
		launch(args);
	}
   
	/**
	 * @brief Prints debug output with newline if debug enabled
	 * @param sDebug String to print
	 */
	public static void debug(String sDebug) {
		if(bDebug) {
			System.err.println(sDebug);
		}
			
	}

	/**
	 * @brief Prints debug output if debug enabled
	 * @param format Formated string to print
	 * @param args Arguments for formated output
	 */
	public static void debug(String format, Object ... args) {
		if(bDebug) {
			String sOutput = format;
			if(!sOutput.endsWith("\n"))
				sOutput += "\n";
			System.err.printf(sOutput, args);
		}
	}

}