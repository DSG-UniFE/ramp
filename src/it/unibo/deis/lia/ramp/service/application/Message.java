/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application;

/**
 *
 * @author useruser
 */
public class Message implements java.io.Serializable{
    
    /**
	 * 
	 */
	private static final long serialVersionUID = 1175422476666091207L;
	
	private String message;

    public Message(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

}
