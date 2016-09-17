package uk.co.riban.esp;

import java.util.LinkedList;

import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/**
 * A class to create a singleton Toast.
 * A Toast is a pop-up notification that does not interfere with program flow and disappears after a short period.
 * The duration and location of the toast may be set. Default is bottom centre and automatically calculate duration from word count.
 * Can be themed using CSS class selector: .toast-notification.
 * @author Brian Walton (2016)
 */
public class Toast extends Popup {
	
	enum Location {
		TOP_LEFT,
		TOP_CENTRE,
		TOP_CENTER,
		TOP_RIGHT,
		LEFT,
		CENTRE,
		CENTER,
		RIGHT,
		BOTTOM_LEFT,
		BOTTOM_CENTRE,
		BOTTOM_CENTER,
		BOTTOM_RIGHT
	};
	
	private static Toast m_toast = null;
	private static Stage m_stage = null;
	private static Label m_label = null;
	private static Integer m_duration = 0;
	private static Location m_location = Location.BOTTOM_CENTRE;
	private static FadeTransition m_fade = null;
	private static LinkedList<String> m_queue = null;
	static final int NOTIFY_DELAY = 500; //Minimum duration (ms) for automatically calculated duration
	static final int NOTIFY_READRATE = 300; //Average duration (ms) to read each word for automatically calculated duration

	//Constructor is only called once for this singleton so we initialise everything
	protected Toast() {
		m_label = new Label("Toast"); //Use a simple label for alert - could create a more complex layout with title and icon but this is just a toast
		m_fade = new FadeTransition(); //Animation for fading in and out
		m_fade.setNode(m_label);
		setAutoFix(true); //ensure it shows on screen
	    m_label.setWrapText(true);
	    //Set default style (first ; is required - don't know why!!!)
	    m_label.setStyle(".toast-notification { ; -fx-text-fill: #D0D0D0; -fx-padding: 5; -fx-background-color: #606060; -fx-background-radius: 10; -fx-font-size: 120%;}");
	    m_label.getStyleClass().add("toast-notification"); //get styling from CSS
	    //constrain size to fit within GUI
    	m_label.setMaxWidth(m_stage.getWidth() - 50);
    	m_label.setMaxHeight(m_stage.getHeight() - 100);
	    //Hide if clicked
	    m_label.setOnMouseReleased(new EventHandler<MouseEvent>() {
	        @Override
	        public void handle(MouseEvent e) {
	        	fadeOut(false);
	        }
	    });
	    getContent().add(m_label);
	    m_queue = new LinkedList<String>();
	}
	
	/**
	 * Set the stage to which the Toast will be attached.
	 * Must be called before Toast can be used.
	 * @param stage Stage to attach Toast to
	 */
	public static final void setStage(Stage stage) {
		m_stage = stage;
	}

	/**
	 * Show the toast
	 * @param message The message to show within the Toast
	 */
	public static final void show(final String message) {

		//Check we have a stage
		if(m_stage == null) {
			System.err.println("Set stage before using Toast by calling Toast.setStage(Stage)");
			return;
		}
		//Create singleton
		if(m_toast == null) {
			m_toast = new Toast();
		}
		m_queue.add(message);
		if(m_toast.isShowing())
			m_toast.fadeOut(false);
		else
			display();
	}
	
	/**
	 * Sets the duration the toast is shown
	 * @param duration Duration in milliseconds to show Toasts. Set to zero to automatically calculate duration from word count.
	 * There is a short fade in and out which is additional to this duration.
	 */
	public static final void setDuration(final int duration) {
		m_duration = duration;
	}

	/**
	 * Set the location for Toast to display
	 * @param location Location to display Toast [TOP_LEFT | TOP_CENTRE | TOP_RIGHT | LEFT | CENTRE | RIGHT | BOTTOM_LEFT | BOTTOM_CENTRE | BOTTOM_RIGHT]
	 * Can also use American spellings of CENTER
	 */
	public static final void setLocation(final Location location) {
		m_location = location;
	}

	/**
	 * Remove all messages from queue.
	 * Useful to stop continual showing of messages that have built up due to an undesriable state
	 */
	public static final void resetQueue() {
		if(m_queue != null)
			m_queue.clear();
	}
	
	/**
	 * Hide toast when user clicks outside toast
	 * enable True to enable auto hide
	 */
	public static final void autoHide(boolean enable) {
		m_toast.setAutoHide(enable);
	}

	/**
	 * Hide toast when user presses escape key
	 * enable True to enable escape hide
	 */
	public static final void escapeHide(boolean enable) {
	    m_toast.setHideOnEscape(enable);
	}
	
	private static void display() {
		if(m_queue.isEmpty())
			return;
	    m_label.setText(m_queue.poll());
	    //Fancy fade in and out
	    m_toast.setOnShown(new EventHandler<WindowEvent>() {
	        @Override
	        public void handle(WindowEvent e) {
	        	switch(m_location) {
		        	case TOP_LEFT:
		        		m_toast.setX(m_stage.getX() + 10);
		        		m_toast.setY(m_stage.getY() + 30);
		        		break;
		        	case TOP_CENTRE:
		        	case TOP_CENTER:
		        		m_toast.setX(m_stage.getX() + m_stage.getWidth()/2 - m_toast.getWidth()/2);
		        		m_toast.setY(m_stage.getY() + 30);
		        		break;
		        	case TOP_RIGHT:
		        		m_toast.setX(m_stage.getX() + m_stage.getWidth() - m_toast.getWidth() - 10);
		        		m_toast.setY(m_stage.getY() + 30);
		        		break;
		        	case LEFT:
		        		m_toast.setX(m_stage.getX() + 10);
		        		m_toast.setY(m_stage.getY() + m_stage.getHeight()/2 - m_toast.getHeight()/2);
		        		break;
		        	case CENTRE:
		        	case CENTER:
		        		m_toast.setX(m_stage.getX() + m_stage.getWidth()/2 - m_toast.getWidth()/2);
		        		m_toast.setY(m_stage.getY() + m_stage.getHeight()/2 - m_toast.getHeight()/2);
		        		break;
		        	case RIGHT:
		        		m_toast.setX(m_stage.getX() + m_stage.getWidth() - m_toast.getWidth() - 10);
		        		m_toast.setY(m_stage.getY() + m_stage.getHeight()/2 - m_toast.getHeight()/2);
		        		break;
		        	case BOTTOM_LEFT:
		        		m_toast.setX(m_stage.getX() + 10);
		        		m_toast.setY(m_stage.getY() + m_stage.getHeight() - m_toast.getHeight() - 10);
		        		break;
		        	case BOTTOM_CENTRE:
		        	case BOTTOM_CENTER:
		        		m_toast.setX(m_stage.getX() + m_stage.getWidth()/2 - m_toast.getWidth()/2);
		        		m_toast.setY(m_stage.getY() + m_stage.getHeight() - m_toast.getHeight() - 10);
		        		break;
		        	case BOTTOM_RIGHT:
		        		m_toast.setX(m_stage.getX() + m_stage.getWidth() - m_toast.getWidth() - 10);
		        		m_toast.setY(m_stage.getY() + m_stage.getHeight() - m_toast.getHeight() - 10);
		        		break;
	        	}
	          fadeIn();
	        	}
	    	});
	    	m_toast.show(m_stage);
	    }

	// Fades in Toast, waits until words can be read then fades out
	private static final void fadeIn() {
        m_fade.setDuration(Duration.millis(600));
        m_fade.setDelay(Duration.millis(0));
        m_fade.setFromValue(0);
        m_fade.setToValue(1);
        //Fade out after delay to read
	    m_fade.setOnFinished(new EventHandler<ActionEvent>() {
	    	@Override
	    	public void handle(ActionEvent e) {
	    		m_toast.fadeOut(true);
	    	}
	    });
	    //Start animation
        m_fade.play();	            
	}

	private void fadeOut(boolean bDelay) {
		if(bDelay) {
			int nWords = m_label.getText().split("\\s+").length; //Quantity of words in message. Used to set time alert is displayed
			if(m_duration == 0)
				m_fade.setDelay(Duration.millis(NOTIFY_DELAY + nWords * NOTIFY_READRATE));
			else
				m_fade.setDelay(Duration.millis(m_duration));
		} else {
			m_fade.setDelay(Duration.millis(0));
		}
		m_fade.setToValue(0);
		m_fade.setFromValue(1);
	    m_fade.setOnFinished(new EventHandler<ActionEvent>() {
	    	@Override
	    	public void handle(ActionEvent event) {
	    		m_toast.hide();
	    		if(!m_queue.isEmpty()) {
	    			show(m_queue.remove());
	    		}
	    	}
	    });
	    m_fade.stop();
		m_fade.play();
	}

}
