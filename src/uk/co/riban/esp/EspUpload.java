package uk.co.riban.esp;

/**
 * @brief	Upload firmware image to ESP
 * @author waltob02
 *
 */
public class EspUpload {
	private String m_sImage;
	EspUpload(String Image) {
		m_sImage = Image;
		System.out.println("Uploading firmware image " + m_sImage);
	}
}
