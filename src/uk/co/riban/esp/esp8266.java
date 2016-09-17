package uk.co.riban.esp;

import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;

/**
 * esp8266 class represents the interface with an ESP8266 via serial interface
 * @author Brian Walton - inspired by esptool by Fredrik Ahlberg, Angus Gratton
 *
 */
public class esp8266 {

	// Request types
	final static int ESP_MSGTYPE_COMMAND  = 0x00;
	final static int ESP_MSGTYPE_RESPONSE = 0x01;
	
    // These are the currently known commands supported by the ROM
	final static int ESP_OP_NONE        = 0x00;
	final static int ESP_OP_FLASH_BEGIN = 0x02;
	final static int ESP_OP_FLASH_DATA  = 0x03;
	final static int ESP_OP_FLASH_END   = 0x04;
	final static int ESP_OP_MEM_BEGIN   = 0x05;
	final static int ESP_OP_MEM_END     = 0x06;
	final static int ESP_OP_MEM_DATA    = 0x07;
	final static int ESP_OP_SYNC        = 0x08;
	final static int ESP_OP_WRITE_REG   = 0x09;
	final static int ESP_OP_READ_REG    = 0x0a;

    // Maximum block sized for RAM and Flash writes, respectively.
	final static int ESP_RAM_BLOCK   = 0x1800;
	final static int ESP_FLASH_BLOCK = 0x400;

    // Default baud rate. The ROM auto-bauds, so we can use more or less whatever we want.
	final static int ESP_ROM_BAUD    = 115200;

    // First byte of the application image
	final static int ESP_IMAGE_MAGIC = 0xe9;

    // Initial state for the checksum routine
	final static int ESP_CHECKSUM_MAGIC = 0xef;

    // OTP ROM addresses
	final static int ESP_OTP_MAC0    = 0x3ff00050;
	final static int ESP_OTP_MAC1    = 0x3ff00054;
	final static int ESP_OTP_MAC2    = 0x3ff00058; //By inference
    final static int ESP_OTP_MAC3    = 0x3ff0005c;

    // Flash sector size, minimum unit of erase.
    final static int ESP_FLASH_SECTOR = 0x1000;
    final static int ESP_FLASH_SECTOR_PER_BLOCK = 16;

    // Message header
    final static int ESP_HEADER_SIZE     = 8;
    final static int ESP_HEADER_MSG_TYPE = 0; //uint8 Defines the type of message
    final static int ESP_HEADER_OP       = 1; //uint8 Defines the operation to perform in a command message
    final static int ESP_HEADER_LEN      = 2; //uint16 Size of message payload
    final static int ESP_HEADER_CHECKSUM = 4; //uint32 Checksum of payload (command message)
    final static int ESP_HEADER_VALUE    = 4; //uint32 Value (response message)

    // Timeouts
    final static int ESP_RESPONSE_RETRY  = 100; //How many times we try to get a response
    final static int ESP_SLIP_TIMEOUT    = 500; //How many times we try to get a response
    
    private SerialPort m_serialPort = null;
    
    esp8266(SerialPort serialport, int baud) throws SerialPortException {
    	m_serialPort = serialport;
    	//Open serial port
		openPort(baud); //!@todo should we be opening port when object is instantiated?
		//!@todo implement slip reader listener on serial port
    }
    
    public boolean openPort(int baud) throws SerialPortException {
    	int nDataBits = 8, nStopBits = SerialPort.STOPBITS_1, nParity = SerialPort.PARITY_NONE;
    	if(m_serialPort == null)
    		return false;
		if(m_serialPort.isOpened())
			m_serialPort.closePort();
		if(!m_serialPort.openPort())
			return false;
		if(!m_serialPort.setParams(baud, nDataBits, nStopBits, nParity)) {
			m_serialPort.closePort();
			return false;
    		}
    	return true;
    }

    /**
     * Reset ESP8266
     * @param bootloader True to boot in to bootloader
     * @return True on success
     * @note Uses RTS and DTS lines as defined for nodeMCU
     */
    public boolean reset(boolean bootloader) {
    	if(m_serialPort == null || !m_serialPort.isOpened())
    		return false;
    	try {
    		/* DTR|RTS||RST|GPI0
    		 *  0 | 0 || 1 | 1
    		 *  0 | 1 || 1 | 0
    		 *  1 | 0 || 0 | 1
    		 *  1 | 1 || 1 | 1
    		 *  GPI0 Low for flash mode, High for run mode
    		 *  RST Low for reset, High for run
    		 *  NODEMCU circuit diagram shows DTS and RTS signals being inverted (NOT outputs)
    		 *  */
    		
    		//!@todo Check whether setRTS/DTR is inverted

    		//Assert reset
    		m_serialPort.setDTR(true);
    		m_serialPort.setRTS(false);
    		Thread.sleep(50);
    		//Choose boot mode
    		m_serialPort.setDTR(false);
    		m_serialPort.setRTS(bootloader);
	    	Thread.sleep(50);
	    	//Free GPI0 port
	    	m_serialPort.setRTS(false);
	    	m_serialPort.closePort();
		} catch(SerialPortException e) {
			Main.debug("Failed to reset ESP8266 - serial port error");
			return false;
		} catch(InterruptedException e) {
			Main.debug("Failed to reset ESP8266 - sleep interrupted");
			return false;
		}
    	return true;
    }
    
	/**
	 * Connect to ESP8266 in programming mode
	 * @return True on success
	 */
	public boolean connect() {
		//!@todo Set appropriate number of reset and sync attempts
		try {
			for(int nAttempt = 0; nAttempt < 4; ++nAttempt) {
				reset(true);
				// worst-case latency timer should be 255ms (probably <20ms)
				Thread.sleep(255);
				m_serialPort.readBytes();
				sync();
			}
		} catch(SerialPortException e) {
			Main.debug("Failed to connect to ESP8266 - serial port error");
		} catch(InterruptedException e) {
			Main.debug("Failed to connect to ESP8266 - sleep interrupted");
		}
		for(int nAttempt = 0; nAttempt < 4; ++nAttempt) {
			sync();
			if(slipRead() != null)
				return true;
		}
		return false;
	}
	
    //Perform a connection test
    private void sync() {
    	int[] array = new int[59];
    	array[0] = 0x07;
    	array[1] = 0x07;
    	array[2] = 0x12;
    	array[3] = 0x20;
    	for(int i=0; i<32; ++i)
    		array[i+4] = 0x55;
        sendCommand(ESP_OP_SYNC, array, 0);
        for(int i=0; i<7; ++i)
            slipRead();
    }
    
    /**
     * Write packet of data to device, performing SLIP escaping
     * @param buffer Byte buffer containing packet data to write to device
     * @return True on success
     */
    public boolean write(int[] buffer) {
    	//A bit wasteful but we create a buffer sufficiently large to handle worse case transform
    	int[] tempBuffer = new int[2 + buffer.length * 2];
    	tempBuffer[0] = 0xc0; //Start byte
    	int nPos = 1; //Cursor used to iterate through temp buffer. Will be size of data at end of iteration.
    	for(int nIndex=0; nIndex < buffer.length; ++nIndex) {
    		if(buffer[nIndex] == 0xdb) {
    			tempBuffer[nPos++] = 0xdb;
    			tempBuffer[nPos++] = 0xdd;
    		} else if(buffer[nIndex] == 0xc0) {
    			tempBuffer[nPos++] = 0xdb;
    			tempBuffer[nPos++] = 0xdc;
    		} else {
    			tempBuffer[nPos++] = buffer[nIndex];
    		}
    	}
    	tempBuffer[nPos++] = 0xc0; //Stop byte
    	//nPos has length of array
    	int[] outBuffer = new int[nPos]; //Create buffer to pass to serial port
    	System.arraycopy(tempBuffer, 0, outBuffer, 0, nPos);
    	try {
			m_serialPort.writeIntArray(outBuffer);
		} catch (SerialPortException e) {
			Main.debug("Failed to write packet to device");
			return false;
		}
    	return true;
    }

    /**
     * Validate or calculate checksum of data block
     * @param data Data block to check
     * @param checksum Checksum value to validate (set to zero to calculate and return checksum of data block)
     * @return If nState is zero, the calculated checksum of the block is returned. If nState is a checksum to validate, zero is returned on success
     */
    public int checksum(int[] data, int checksum)
    {
        int nChecksum = checksum;
        for(int nIndex = 0; nIndex < data.length; ++nIndex)
            nChecksum ^= data[nIndex];
        return nChecksum;
    }

    /**
     * Send a command and await response
     * @param operation Command operation code
     * @param data Data block associated with operation
     * @param checksum Checksum of data
     * @return Response from device as integer array or null if no response recieved
     */
    public int[] sendCommand(int operation, int[] data, int checksum) {
    	int[] buffer = new int[data.length + ESP_HEADER_SIZE];
    	/*Populate header
    		byte message type
    		byte operation code
    		short length of payload
    		int checksum
    	*/
    	buffer[ESP_HEADER_MSG_TYPE] = ESP_MSGTYPE_COMMAND; //Message type
    	buffer[ESP_HEADER_OP] = operation; //request command operation
    	//!@todo check little / big endian
    	buffer[ESP_HEADER_LEN] = data.length & 0xFF;
    	buffer[ESP_HEADER_LEN + 1] = (data.length >> 8) & 0xFF;
    	//int[] checksum = toIntArray(checksum(data, 0));
    	System.arraycopy(checksum, 0, buffer, ESP_HEADER_CHECKSUM, 4);
    	System.arraycopy(data, 0, buffer, ESP_HEADER_SIZE, data.length);
    	write(buffer);
    	return readResponse(operation);
    }
    

    //Check all received data for a message response to 'operation'. Return the payload.
    private int[] readResponse(int operation) {    	
    	int[] result;
    	//Try several times to get an appropriate header but not indefinitely
    	for(int nCount = 0; nCount  < ESP_RESPONSE_RETRY; ++nCount) {
    		result = slipRead();
    		if(result == null)
    			return null; //no more data
    		if(result.length < ESP_HEADER_SIZE)
    			continue; //too short for a header
    		if(result[ESP_HEADER_MSG_TYPE] != ESP_MSGTYPE_RESPONSE)
    			continue; //not a response message
            if((operation == ESP_OP_NONE) || (result[ESP_HEADER_OP] == operation)) {
            	//Got the response we were looking for
            	int[] response = new int[result.length - 8];
            	System.arraycopy(result, 8, response, 0, result.length - 8);
                return response;
            }
        }
        return null;    	
    }
    
    /**
     * Reads a message from ESP8266, decoding using SLIP escaping
     * @return Received message as integer array or null on timeout
     */
    private int[] slipRead() {
        boolean bInEscapeSeq = false;
        while(true) {
	    	try {
	        	int[] result = null;
	        	int nWaiting = m_serialPort.getInputBufferBytesCount();
	    		int[] array = m_serialPort.readIntArray((nWaiting==0)?1:nWaiting, ESP_SLIP_TIMEOUT);
				if(array == null || array.length == 0)
					return null;
				int nPos = 0; //position within result array
				for(int nCursor: array) {
					if(result == null)
						if(nCursor == 0xc0)
							result = new int[nWaiting];
						else
							return null;
					else if(bInEscapeSeq) {
						bInEscapeSeq = false;
						if(nCursor == 0xdc)
							result[nPos++] = 0xc0;
						else if(nCursor == 0xdd)
							result[nPos++] = 0xdb;
						else {
							Main.debug("Invalid SLIP escape 0xdb %0x", nCursor);
							return null;
						}
					}
					else if(nCursor == 0xdb)
						bInEscapeSeq = true;
					else if(nCursor == 0xc0)
						return result;
					else
						result[nPos++] = nCursor;
				}
			} catch(SerialPortException e) {
				Main.debug("Serial port error reading response from ESP8266");
			} catch(SerialPortTimeoutException e) {
				Main.debug("Timeout awaiting valid response from ESP8266");
			}
        }
    }

/*    """ Read MAC from OTP ROM """
    private void readMac() {
        mac0 = self.read_reg(self.ESP_OTP_MAC0)
        mac1 = self.read_reg(self.ESP_OTP_MAC1)
        mac3 = self.read_reg(self.ESP_OTP_MAC3)
        if (mac3 != 0):
            oui = ((mac3 >> 16) & 0xff, (mac3 >> 8) & 0xff, mac3 & 0xff)
        elif ((mac1 >> 16) & 0xff) == 0:
            oui = (0x18, 0xfe, 0x34)
        elif ((mac1 >> 16) & 0xff) == 1:
            oui = (0xac, 0xd0, 0x74)
        else:
            raise FatalError("Unknown OUI")
        return oui + ((mac1 >> 8) & 0xff, mac1 & 0xff, (mac0 >> 24) & 0xff)
    }
*/

    /** Read memory address in target
     * 
     * @return Value from memory address
     * @throws Exception if memory cannot be read
     */
    private int readReg(int addr) throws Exception {
    	int[] result = sendCommand(ESP_OP_READ_REG, toIntArray(addr), 0);
        if(result == null || (result[1] != 0x00) && (result[2] != 0x00)) {
            throw new Exception(); //!@todo define exception
        }
        return toInteger(result);
    }

    /**
     * Sends a command that requires integer arguments (many commands use this format).
     * @param command Operational command for ESP
     * @param data Integer array of data to send. Set to null if not required.
     * @param params Variable quantity of integer parameters
     * Returns true on success.
    */
    private boolean commonCommand(int command, int[] data, int... params) {
    	int[] buffer = new int[4 * params.length + ((data==null)?0:data.length)];
    	int nPos = 0;
    	for(int arg: params) {
    		System.arraycopy(toIntArray(arg), 0, buffer, nPos+=4, 4);
    	}
       	if(data != null)
    		System.arraycopy(data, 0, buffer, nPos, data.length);
    	int[] response = sendCommand(command, buffer, (data==null)?0:checksum(data, 0));
    	if(response == null || response.length < 2 || response[0] != 0 || response[1] !=0)
    		return false;
    	return true;
    }
    

    /**
     * Write to a register
     * @param address Register address
     * @param value Value to write to register
     * @param mask
     * @param delay_us
     * @return true on success
     */
    private boolean writeReg(int address, int value, int mask, int delay_us) {
    	return(commonCommand(ESP_OP_WRITE_REG, null, address, value, mask, delay_us));
    }

    /**
     * Begin writing image to RAM.
     * @param size Total quantity of bytes in data (may be less than blocks * blocksize if last block is short)
     * @param blocks Quantity of blocks
     * @param blocksize Quantity of bytes in each block
     * @param offset Memory offset to start writing to
     * @return True on success
     */
    private boolean memBegin(int size, int blocks, int blocksize, int offset) {
    	return(commonCommand(ESP_OP_MEM_BEGIN, null, size, blocks, blocksize, offset));
    }
    
    /**
     * Write a block of data to memory. Returns true on success.
     * @param data Data block to write
     * @param seq Sequence number of block
     * @return True on success
     */
    private boolean memBlock(int[] data, int seq) {
    	return(commonCommand(ESP_OP_MEM_DATA, data, data.length, seq, 0, 0));
    }

    /**
     * Finish writing image to RAM
     * @param entryPoint Pointer to first instruction to run in code
     * @return True on success
     */
    private boolean memFinish(int entryPoint) {
    	return(commonCommand(ESP_OP_MEM_END, null, (entryPoint==0?1:0), entryPoint));
    }
    
    /**
     * Begins writing image to flash (erases blocks first)
     * @param size Quantity of bytes to write to flash
     * @param offset Address of Flash to write to
     * @return True on success
     */
    public boolean flashBegin(int size, int offset) {
    	int nBlocks = (size + ESP_FLASH_BLOCK -1) / ESP_FLASH_BLOCK;
    	int nSectors = (size + ESP_FLASH_SECTOR -1) / ESP_FLASH_SECTOR;
    	int nStartSector = offset / ESP_FLASH_SECTOR;    	
    	int nHeadSector = ESP_FLASH_SECTOR_PER_BLOCK - (nStartSector %  ESP_FLASH_SECTOR_PER_BLOCK);
    	if(nSectors < nHeadSector)
    		nHeadSector = nSectors;
    	int nEraseSize;
    	if(nSectors < 2 * nHeadSector)
    		nEraseSize = (nSectors + 1) / 2 * ESP_FLASH_SECTOR;
    	else
    		nEraseSize = (nSectors - nHeadSector) * ESP_FLASH_SECTOR;
    	
    	return commonCommand(ESP_FLASH_BLOCK, null, nEraseSize, nBlocks, ESP_FLASH_BLOCK, offset);    	
    }
    
    /**
     * Write a block of data to flash
     * @param data Block of data to write
     * @param seq Block sequence number
     * @return True on success
     */
    public boolean flashBlock(int[] data, int seq) {
    	return(commonCommand(ESP_OP_FLASH_DATA, data, data.length, seq, 0, 0));
    }

    /**
     * Finish writing image to Flash
     * @param reboot True to reboot ESP8266 after completion
     * @return true on success
     */
    public boolean flashFinish(boolean reboot) {
    	return(commonCommand(ESP_OP_FLASH_END, null, (reboot?0:1)));
    }

    /**
     * Run application code in flash
     * @param reboot True to perform reboot first
     * @return True on success
     */
    public boolean run(boolean reboot) {
    	//Perform empty flash cycle
    	return(flashBegin(0, 0) && flashFinish(reboot));
    }

    /** Read MAC
     * @return ESP8288 MAC as 6 element integer array
     * @throws Exception 
     */
    int[] getMac() throws Exception {
    	//!@todo what use is this?
    	int[] mac = new int[6];
    	int mac0 = readReg(ESP_OTP_MAC0);
    	int mac1 = readReg(ESP_OTP_MAC1);
        int mac3 = readReg(ESP_OTP_MAC3);
        if(mac3 != 0) {
        	mac = new int[4];
            mac[0] = (mac3 >> 16) & 0xff;
            mac[1] = (mac3 >> 8) & 0xff;
            mac[2] = mac3 & 0xff;
        } else if(((mac1 >> 16) & 0xff) == 0) {
        	mac[0] = 0x18;
        	mac[1] = 0xfe;
        	mac[2] = 0x34;
        }
        else if(((mac1 >> 16) & 0xff) == 1) {
        	mac[0] = 0xac;
        	mac[1] = 0xd0;
        	mac[2] = 0x74;
        }
        else
        	throw new Exception();
        mac[3] = (mac1 >> 8) & 0xff;
        mac[4] = mac1 & 0xff;
        mac[5] = (mac0 >> 24) & 0xff;
        return mac;
    }
    
    public int getChipId() throws Exception {
    	int id0 = readReg(ESP_OTP_MAC0);
    	int id1 = readReg(ESP_OTP_MAC1);
    	return(id0 >> 24) | (id1 & 0xffffff) << 8;
    }

    /**
     * Read SPI flash manufacturer and device id
     * @return ID or zero on failure
     * @throws Exception 
     */
    public int getFlashId() throws Exception {
        flashBegin(0, 0);
        writeReg(0x60000240, 0x0, 0xffffffff, 0);
        writeReg(0x60000200, 0x10000000, 0xffffffff, 0);
        int flashId = readReg(0x60000240);
        flashFinish(false);
        return flashId;
    }

    /**
     * Converts an integer to an array representing the little endian byte representation
     * @param value Value to convert
     * @return Integer array representing little endian presentation
     */
    private int[] toIntArray(int value) {
    	int[] intArray = new int[4];
    	intArray[0] = (value & 0xFF);
    	intArray[1] = ((value >> 8) & 0xFF);
    	intArray[2] = ((value >> 16) & 0xFF);
    	intArray[3] = ((value >> 24) & 0xFF);
    	return intArray;
    }

    /**
     * Converts little endian byte representaton array to integer
     * @param array 4 element array containing little endian byte representation
     * @return Integer value or zero if invalid array
     */
    private int toInteger(int[] array) {
    	if(array == null || array.length < 4)
    		return 0;
    	return (array[0] & (array[1] << 8) & (array[2] << 16) & (array[3] << 24));
    }

}
