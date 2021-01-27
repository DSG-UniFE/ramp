package it.unibo.deis.lia.ramp.service.application;
import java.lang.Number;
import java.nio.charset.Charset;
import java.util.Random;
import com.google.gson.Gson;

/**
 +---------------+------+---------+
 |         SenML | JSON | Type    |
 +---------------+------+---------+
 |     Base Name | bn   | String  |
 |     Base Time | bt   | Number  |
 |     Base Unit | bu   | String  |
 |    Base Value | bv   | Number  |
 |       Version | bver | Number  |
 |          Name | n    | String  |
 |          Unit | u    | String  |
 |         Value | v    | Number  |
 |  String Value | vs   | String  |
 | Boolean Value | vb   | Boolean |
 |    Data Value | vd   | String  |
 |     Value Sum | s    | Number  |
 |          Time | t    | Number  |
 |   Update Time | ut   | Number  |
 +---------------+------+---------+
 */

class SenMlMex {

    private String bn;

    private Number bt;

    private String bu;

    private Number bv, bver;

    private String n, u;

    private Number v;

    private String vs;

    private Boolean vb;

    private String vd;

    private Number s, t, ut;

    private static SenMlMex senml_instance = null;

    public String generateRandomString() {
//        byte[] array = new byte[7]; // length is bounded by 7
//        new Random().nextBytes(array);
//        String generatedString = new String(array, Charset.forName("UTF-8"));
        String generatedString = generateAlphaNumericString(10);
        return generatedString;
    }

    public String generateAlphaNumericString(int size){

        // length is bounded by 256 Character
        byte[] array = new byte[256];
        new Random().nextBytes(array);

        String randomString
                = new String(array, Charset.forName("UTF-8"));

        // Create a StringBuffer to store the result
        StringBuffer r = new StringBuffer();

        // remove all spacial char
        String  AlphaNumericString
                = randomString
                .replaceAll("[^A-Za-z0-9]", "");

        // Append first 20 alphanumeric characters
        // from the generated random String into the result
        for (int k = 0; k < AlphaNumericString.length(); k++) {

            if (Character.isLetter(AlphaNumericString.charAt(k)) && (size > 0) || Character.isDigit(AlphaNumericString.charAt(k)) && (size > 0)) {
                r.append(AlphaNumericString.charAt(k));
                size--;
            }
        }

        // return the resultant string
        return r.toString();
    }

    public int generateRandomInt(int max, int min) {
        int generatedInt = (int)(Math.random() * (max - min + 1) + min);
        return generatedInt;
    }
    public Boolean generateRandomBoolean() {
        Random generatedRandom = new Random();
        return generatedRandom.nextBoolean();
    }

    private SenMlMex (){
        this.bn = generateRandomString();
        this.bt = generateRandomInt(50, 100);
        this.bu = generateRandomString();
        this.bv = generateRandomInt(50, 100);
        this.bver = generateRandomInt(50, 100);
        this.n = generateRandomString();
        this.u = generateRandomString();
        this.v = generateRandomInt(50, 100);
        this.vs = generateRandomString();
        this.vb = generateRandomBoolean();
        this.vd = generateRandomString();
        this.s = generateRandomInt(50, 100);
        this.t = generateRandomInt(50, 100);
        this.ut = generateRandomInt(50, 100);
    }

    public static synchronized SenMlMex getInstance() {
        if (senml_instance == null) {
            senml_instance = new SenMlMex();
        }
        return senml_instance;
    }

    public String getBaseName() {
        return bn;
    }

    public void setBaseName(String bn) {
        this.bn = bn;
    }

    public Number getBaseTime() {
        return bt;
    }

    public void setBaseTime(Number bt) {
        this.bt = bt;
    }

    public String getBaseUnit() {
        return bu;
    }

    public void setBaseUnit(String bu) {
        this.bu = bu;
    }

    public Number getBaseValue() {
        return bv;
    }

    public void setBaseValue(Number bv) {
        this.bv = bv;
    }

    public Number getVersion() {
        return bver;
    }

    public void setVersion(Number bver) {
        this.bver = bver;
    }

    public String getName() {
        return n;
    }

    public void setName(String n) {
        this.n = n;
    }

    public String getUnit() {
        return u;
    }

    public void setUnit(String u) {
        this.u = u;
    }

    public Number getValue() {
        return v;
    }

    public void setValue(Number v) {
        this.v = v;
    }

    public String getStringValue() {
        return vs;
    }

    public void setStringValue(String vs) {
        this.vs = vs;
    }

    public Boolean getBooleanValue() {
        return vb;
    }

    public void setBooleanValue(Boolean vb) {
        this.vb = vb;
    }

    public String getDataValue() {
        return vd;
    }

    public void setDataValue(String vd) {
        this.vd = vd;
    }

    public Number getValueSum() {
        return s;
    }

    public void setValueSum(Number s) {
        this.s = s;
    }

    public Number getTime() {
        return t;
    }

    public void setTime(Number t) {
        this.t = t;
    }

    public Number getUpdateTime() {
        return ut;
    }

    public void setUpdateTime(Number ut) {
        this.ut = ut;
    }

    public String toJsonString(){
        Gson gson = new Gson();
        return gson.toJson(senml_instance);
    }

    @Override
    public String toString() {
        return "SenML [ " + (bn != null ? "bn=" + bn + "  " : "") + (bt != null ? "bt=" + bt + "  " : "")
                + (bu != null ? "bu=" + bu + "  " : "") + (bv != null ? "bv=" + bv + "  " : "")
                + (bver != null ? "bver=" + bver + "  " : "") + (n != null ? "n=" + n + "  " : "")
                + (u != null ? "u=" + u + "  " : "") + (v != null ? "v=" + v + "  " : "")
                + (vs != null ? "vs=" + vs + "  " : "") + (vb != null ? "vb=" + vb + "  " : "")
                + (vd != null ? "vd=" + vd + "  " : "") + (s != null ? "s=" + s + "  " : "")
                + (t != null ? "t=" + t + "  " : "") + (ut != null ? "ut=" + ut + "  " : "") + "]";
    }

}
