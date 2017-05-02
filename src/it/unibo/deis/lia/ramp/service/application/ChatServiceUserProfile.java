package it.unibo.deis.lia.ramp.service.application;

public class ChatServiceUserProfile implements java.io.Serializable{

	private static final long serialVersionUID = 4898447069449183148L;
	
	private String firstName;
	private String lastName; 
	private String birthdate;
	private int timeout;
	private int ttl;
	private int serviceAmount;
	private int expiry;
    private byte[] profilePicture;
	private String bxMessage;
	
	public ChatServiceUserProfile(String firstName, String lastName, String birthdate, byte[] profilePicture,
			int ttl, int timeout, int expiry, int serviceAmount) {
	       this.firstName = firstName;
	       this.lastName = lastName;
	       this.birthdate = birthdate;
	       this.profilePicture = profilePicture;
	       this.expiry = expiry;
	       this.ttl = ttl;
	       this.serviceAmount = serviceAmount;
	       this.timeout = timeout;
	       bxMessage = "";
	   }
	
	   public String getFirstName() {
	       return this.firstName;
	   }
	   
	   public void setFirstName(String firstName) {
	       this.firstName = firstName;
	   }
	   
	   public String getLastName() {
	       return this.lastName;
	   }
	   
	   public void setLastName(String lastName) {
	       this.lastName = lastName;
	   }
	   
	   public String getBirthdate() {
			return birthdate;
	   }
	   
	   public void setBirthdate(String birthdate) {
	       this.birthdate = birthdate;
	   }
	   
	   public byte[] getProfilePicture() {
			return profilePicture;
	   }
	   
	   public void setProfilePicture(byte[] profilePicture) {
	       this.profilePicture = profilePicture;
	   }
	   
	   public int getTTL() {
			return ttl;
	   }
	   
	   public void setTTL(int ttl) {
	       this.ttl = ttl;
	   }
	   
	   public int getServiceAmount() {
			return serviceAmount;
	   }
	   
	   public void setServiceAmount(int serviceAmount) {
	       this.serviceAmount = serviceAmount;
	   }
	   
	   public int getTimeout() {
			return timeout;
	   }
	   
	   public void setTimeout(int timeout) {
	       this.timeout = timeout;
	   }
	   
	   public int getExpiry() {
			return expiry;
	   }
	   
	   public void setExpiry(int expiry) {
	       this.expiry = expiry;
	   }
	   
	   public String getBxMessage() {
			return bxMessage;
	   }
	   
	   public void setBxMessage(String bxMessage) {
	       this.bxMessage = bxMessage;
	   }
}
