package com.jabcode.test.calibration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Date;

public class CalibrationProfile {
    private String version;
    private Date created;
    private PrinterInfo printer;
    private CameraInfo camera;
    private ColorMapping colorMapping;
    private QualityMetrics quality;
    
    public static class PrinterInfo {
        public String model;
        public String type;
        public String userNotes;
        
        public PrinterInfo(String model, String type, String userNotes) {
            this.model = model;
            this.type = type;
            this.userNotes = userNotes;
        }
        
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("model", model);
            json.put("type", type);
            json.put("user_notes", userNotes);
            return json;
        }
        
        public static PrinterInfo fromJson(JSONObject json) throws JSONException {
            return new PrinterInfo(
                json.getString("model"),
                json.getString("type"),
                json.getString("user_notes")
            );
        }
    }
    
    public static class CameraInfo {
        public String device;
        public Date captureDate;
        
        public CameraInfo(String device, Date captureDate) {
            this.device = device;
            this.captureDate = captureDate;
        }
        
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("device", device);
            json.put("captureDate", captureDate.getTime());
            return json;
        }
        
        public static CameraInfo fromJson(JSONObject json) throws JSONException {
            return new CameraInfo(
                json.getString("device"),
                new Date(json.getLong("captureDate"))
            );
        }
    }
    
    public static class RGBColor {
        public int r, g, b;
        
        public RGBColor(int r, int g, int b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
        
        public JSONArray toJson() {
            JSONArray json = new JSONArray();
            json.put(r);
            json.put(g);
            json.put(b);
            return json;
        }
        
        public static RGBColor fromJson(JSONArray json) throws JSONException {
            return new RGBColor(
                json.getInt(0),
                json.getInt(1),
                json.getInt(2)
            );
        }
        
        public float distanceTo(RGBColor other) {
            int dr = this.r - other.r;
            int dg = this.g - other.g;
            int db = this.b - other.b;
            return (float) Math.sqrt(dr*dr + dg*dg + db*db);
        }
    }
    
    public static class ColorMap {
        public RGBColor standard;
        public RGBColor calibrated;
        
        public ColorMap(RGBColor standard, RGBColor calibrated) {
            this.standard = standard;
            this.calibrated = calibrated;
        }
        
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("standard", standard.toJson());
            json.put("calibrated", calibrated.toJson());
            return json;
        }
        
        public static ColorMap fromJson(JSONObject json) throws JSONException {
            return new ColorMap(
                RGBColor.fromJson(json.getJSONArray("standard")),
                RGBColor.fromJson(json.getJSONArray("calibrated"))
            );
        }
    }
    
    public static class ColorMapping {
        public ColorMap red;
        public ColorMap green;
        public ColorMap blue;
        public ColorMap white;
        public ColorMap magenta;
        
        public RGBColor black = new RGBColor(0, 0, 0);
        public RGBColor yellow = new RGBColor(255, 255, 0);
        public RGBColor cyan = new RGBColor(0, 255, 255);
        
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("red", red.toJson());
            json.put("green", green.toJson());
            json.put("blue", blue.toJson());
            json.put("white", white.toJson());
            json.put("magenta", magenta.toJson());
            
            JSONObject fixed = new JSONObject();
            fixed.put("black", black.toJson());
            fixed.put("yellow", yellow.toJson());
            fixed.put("cyan", cyan.toJson());
            json.put("fixedColors", fixed);
            
            return json;
        }
        
        public static ColorMapping fromJson(JSONObject json) throws JSONException {
            ColorMapping mapping = new ColorMapping();
            mapping.red = ColorMap.fromJson(json.getJSONObject("red"));
            mapping.green = ColorMap.fromJson(json.getJSONObject("green"));
            mapping.blue = ColorMap.fromJson(json.getJSONObject("blue"));
            mapping.white = ColorMap.fromJson(json.getJSONObject("white"));
            mapping.magenta = ColorMap.fromJson(json.getJSONObject("magenta"));
            return mapping;
        }
        
        public RGBColor getCalibratedColor(int standardR, int standardG, int standardB) {
            if (standardR == 255 && standardG == 0 && standardB == 0) return red.calibrated;
            if (standardR == 0 && standardG == 255 && standardB == 0) return green.calibrated;
            if (standardR == 0 && standardG == 0 && standardB == 255) return blue.calibrated;
            if (standardR == 255 && standardG == 255 && standardB == 255) return white.calibrated;
            if (standardR == 255 && standardG == 0 && standardB == 255) return magenta.calibrated;
            if (standardR == 0 && standardG == 0 && standardB == 0) return black;
            if (standardR == 255 && standardG == 255 && standardB == 0) return yellow;
            if (standardR == 0 && standardG == 255 && standardB == 255) return cyan;
            return new RGBColor(standardR, standardG, standardB);
        }
    }
    
    public static class QualityMetrics {
        public float averageColorDistance;
        public float redMagentaSeparation;
        public float minSeparation;
        
        public QualityMetrics(float avgDistance, float redMagentaSep, float minSep) {
            this.averageColorDistance = avgDistance;
            this.redMagentaSeparation = redMagentaSep;
            this.minSeparation = minSep;
        }
        
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("averageColorDistance", averageColorDistance);
            json.put("redMagentaSeparation", redMagentaSeparation);
            json.put("minSeparation", minSeparation);
            return json;
        }
        
        public static QualityMetrics fromJson(JSONObject json) throws JSONException {
            return new QualityMetrics(
                (float) json.getDouble("averageColorDistance"),
                (float) json.getDouble("redMagentaSeparation"),
                (float) json.getDouble("minSeparation")
            );
        }
        
        public String getQualityLevel() {
            if (minSeparation >= 100) return "Excellent";
            if (minSeparation >= 50) return "Acceptable";
            if (minSeparation >= 30) return "Warning";
            return "Poor";
        }
    }
    
    public CalibrationProfile() {
        this.version = "1.0";
        this.created = new Date();
    }
    
    public String getVersion() { return version; }
    public Date getCreated() { return created; }
    public PrinterInfo getPrinter() { return printer; }
    public CameraInfo getCamera() { return camera; }
    public ColorMapping getColorMapping() { return colorMapping; }
    public QualityMetrics getQuality() { return quality; }
    
    public void setPrinter(PrinterInfo printer) { this.printer = printer; }
    public void setCamera(CameraInfo camera) { this.camera = camera; }
    public void setColorMapping(ColorMapping mapping) { this.colorMapping = mapping; }
    public void setQuality(QualityMetrics quality) { this.quality = quality; }
    
    public String toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("version", version);
        json.put("created", created.getTime());
        json.put("printer", printer.toJson());
        json.put("camera", camera.toJson());
        json.put("colorMapping", colorMapping.toJson());
        json.put("quality", quality.toJson());
        return json.toString(2);
    }
    
    public static CalibrationProfile fromJson(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);
        CalibrationProfile profile = new CalibrationProfile();
        profile.version = json.getString("version");
        profile.created = new Date(json.getLong("created"));
        profile.printer = PrinterInfo.fromJson(json.getJSONObject("printer"));
        profile.camera = CameraInfo.fromJson(json.getJSONObject("camera"));
        profile.colorMapping = ColorMapping.fromJson(json.getJSONObject("colorMapping"));
        profile.quality = QualityMetrics.fromJson(json.getJSONObject("quality"));
        return profile;
    }
    
    public String getId() {
        return printer.model.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
    }
}
