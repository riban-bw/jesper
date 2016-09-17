package uk.co.riban.esp;

import java.io.File;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;

public class FirmwareConfig extends HBox {
	
	static public final String[] OFFSETS = {"0x000000", "0x001000", "0x010000", "0x03C000", "0x03E000", "0x040000", "0x07C000", "0x07E000", "0x0FC000", "0x0FE000", "0x1FC000", "0x1FE000", "0x3FC000", "0x3FE000", "0x7FC000", "0x7FE000", "0xFFC000", "0xFFE000"};

	CheckBox m_chkEnable;
	TextField m_txtPath;
	Button m_btnBrowse;
	ComboBox<String> m_cmbOffset;
	
	FirmwareConfig() {
		m_chkEnable = new CheckBox();
		m_txtPath = new TextField();
		m_txtPath.setPromptText("Firmware filename");
		m_btnBrowse = new Button("...");
		m_btnBrowse.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
		    	FileChooser chooser = new FileChooser();
		    	chooser.setTitle("Select firmware image");
		    	File file = chooser.showOpenDialog(Main.pStage);
		    	if(file == null)
		    		return;
				m_txtPath.setText(file.getPath());
			}
		});
		m_cmbOffset = new ComboBox<String>();
		m_cmbOffset.setPromptText("Offset");
		m_cmbOffset.setPrefWidth(100);
		m_cmbOffset.getItems().addAll(OFFSETS);
		m_cmbOffset.setEditable(true);
		setHgrow(m_txtPath, Priority.ALWAYS);
		setMargin(m_chkEnable, new Insets(5,0,5,5));
		getChildren().addAll(m_chkEnable, m_txtPath, m_btnBrowse, m_cmbOffset);
	}
	
	boolean isEnabled() {
		return m_chkEnable.isSelected();
	}
	
	String getPath() {
		return m_txtPath.getText();
	}
	
	String getOffset() {
		return m_cmbOffset.getValue();
	}
	
	void setEnabled(boolean bEnable) {
		m_chkEnable.setSelected(bEnable);
	}
	
	void setPath(String sPath) {
		m_txtPath.setText(sPath);
	}
	
	void setOffset(String sOffset) {
		m_cmbOffset.setValue(sOffset);
	}
}
