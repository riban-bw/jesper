package uk.co.riban.esp;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/**	@brief	A JavaFX Tab providing serial port terminal emulator
 * 
 * @author	Brian Walton
 * @note	Requires TerminalTab.fxml GUI layout file
 */
public class TerminalTab extends Tab {

	TerminalTabController controller;
	Label lblTabTitle;
	static int nCount = 0;
	public int nId;
	
	public TerminalTab(String Title) {
		try {
			// Load GUI from fxml
			final FXMLLoader loader = new FXMLLoader(getClass().getResource("TerminalTab.fxml"));
			final Parent root = loader.load();
			this.setContent(root);
			controller = loader.<TerminalTabController>getController(); 

			// Editable tab title
			lblTabTitle = new Label(Title); // The label to show in the tab title
			setGraphic(lblTabTitle);
			final TextField textField = new TextField(); // Used to enter new title
			// Handle mouse click to set title
			lblTabTitle.setOnMouseClicked(new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent event) {
					if (event.getClickCount() == 2) {
						textField.setText(lblTabTitle.getText()); // Set editable text with current title
						setGraphic(textField); // Show the editable text
						textField.selectAll(); // We want to change title so let's select it all
						textField.requestFocus(); // And give keyboard input focus
					}
				}
			});
			// Handle entering the new title
			textField.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent event) {
					lblTabTitle.setText(textField.getText()); // Update title label...
					setGraphic(lblTabTitle); // ...and show it
				}
			});
			// Handle moving focus away from new title
			textField.focusedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
					if (!newValue) {
						setGraphic(lblTabTitle);
					}
				}
			});
			//Handle pressing escape key whilst editing title (return to previous title)
			textField.setOnKeyReleased(new EventHandler<KeyEvent>() {
				@Override
				public void handle(KeyEvent keyEvent) {
			    	if(keyEvent.getCode().equals(KeyCode.ESCAPE))
			    	{
			    		lblTabTitle.setText(textField.getText());
						setGraphic(lblTabTitle);
			        }
			    }
			});
			
			setOnClosed(new EventHandler<Event>() {
				@Override
				public void handle(Event event) {
					controller.closePort(false); //Only closes port when tab is closed, not when application closes
				}
			});

		} catch (Exception e) {
			System.err.println("Failed to create terminal tab. " + e.getMessage());
		}
		
		setOnSelectionChanged(new EventHandler<Event>() {

			@Override
			public void handle(Event event) {
				if(isSelected()) {
					controller.setFocus();
				}
			}
		});
		nId = ++nCount;
	}


	/**
	 * @brief	Requests the terminal suspends serial port
	 * @param	port Name of the port to suspend
	 * @return	boolean True on success
	 * @note	Does nothing if port not open
	 */
	public boolean suspendPort(String port) {
		return controller.suspendPort(port);
	}
	
	/**
	 * Resumes a suspended serial port
	 * @return	void
	 * @note	Does nothing if port not suspended or different port name
	 */
	public void resumePort() {
		controller.resumePort();
	}
	
	void refreshPort() {
		controller.refreshPort();
	}
	
	public TerminalTabController getController() {
		return controller;
	}
		
	void saveConfig(int nTab) {
		nId = nTab;
		Main.props.setProperty("Terminal_" + Integer.toString(nId), String.format("%s,%s,%d,%d,%s,%s", 
				lblTabTitle.getText(), controller.getPort(), controller.getBaud(), controller.getBits(), controller.GetParity(), controller.getStop()));
	}
		
	void closePort() {
		controller.closePort(false);
	}
}
