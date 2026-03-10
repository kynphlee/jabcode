package com.jabcode.test.calibration;

import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Date;

public class CalibrationProfileTest {
    
    @Test
    public void testProfileCreation() {
        CalibrationProfile profile = new CalibrationProfile();
        assertNotNull(profile);
        assertEquals("1.0", profile.getVersion());
        assertNotNull(profile.getCreated());
    }
    
    @Test
    public void testColorMapping() {
        CalibrationProfile.ColorMapping mapping = new CalibrationProfile.ColorMapping();
        
        CalibrationProfile.RGBColor stdRed = new CalibrationProfile.RGBColor(255, 0, 0);
        CalibrationProfile.RGBColor calRed = new CalibrationProfile.RGBColor(240, 10, 5);
        mapping.red = new CalibrationProfile.ColorMap(stdRed, calRed);
        
        CalibrationProfile.RGBColor result = mapping.getCalibratedColor(255, 0, 0);
        assertEquals(240, result.r);
        assertEquals(10, result.g);
        assertEquals(5, result.b);
    }
    
    @Test
    public void testFixedColorsUnchanged() {
        CalibrationProfile.ColorMapping mapping = new CalibrationProfile.ColorMapping();
        
        CalibrationProfile.RGBColor black = mapping.getCalibratedColor(0, 0, 0);
        assertEquals(0, black.r);
        assertEquals(0, black.g);
        assertEquals(0, black.b);
        
        CalibrationProfile.RGBColor yellow = mapping.getCalibratedColor(255, 255, 0);
        assertEquals(255, yellow.r);
        assertEquals(255, yellow.g);
        assertEquals(0, yellow.b);
        
        CalibrationProfile.RGBColor cyan = mapping.getCalibratedColor(0, 255, 255);
        assertEquals(0, cyan.r);
        assertEquals(255, cyan.g);
        assertEquals(255, cyan.b);
    }
    
    @Test
    public void testColorDistance() {
        CalibrationProfile.RGBColor red = new CalibrationProfile.RGBColor(255, 0, 0);
        CalibrationProfile.RGBColor magenta = new CalibrationProfile.RGBColor(255, 0, 255);
        
        float distance = red.distanceTo(magenta);
        assertEquals(255.0f, distance, 0.1f);
    }
    
    @Test
    public void testQualityMetrics() {
        CalibrationProfile.QualityMetrics excellent = new CalibrationProfile.QualityMetrics(185.7f, 303.06f, 150.0f);
        assertEquals("Excellent", excellent.getQualityLevel());
        
        CalibrationProfile.QualityMetrics acceptable = new CalibrationProfile.QualityMetrics(120.0f, 150.0f, 75.0f);
        assertEquals("Acceptable", acceptable.getQualityLevel());
        
        CalibrationProfile.QualityMetrics warning = new CalibrationProfile.QualityMetrics(80.0f, 100.0f, 45.0f);
        assertEquals("Warning", warning.getQualityLevel());
        
        CalibrationProfile.QualityMetrics poor = new CalibrationProfile.QualityMetrics(50.0f, 60.0f, 20.0f);
        assertEquals("Poor", poor.getQualityLevel());
    }
    
    @Test
    public void testJsonSerialization() throws JSONException {
        CalibrationProfile profile = new CalibrationProfile();
        
        profile.setPrinter(new CalibrationProfile.PrinterInfo("Canon Pixma iP4000", "inkjet", "Home printer"));
        profile.setCamera(new CalibrationProfile.CameraInfo("Google Pixel 5", new Date()));
        
        CalibrationProfile.ColorMapping mapping = new CalibrationProfile.ColorMapping();
        mapping.red = new CalibrationProfile.ColorMap(
            new CalibrationProfile.RGBColor(255, 0, 0),
            new CalibrationProfile.RGBColor(240, 10, 5)
        );
        mapping.green = new CalibrationProfile.ColorMap(
            new CalibrationProfile.RGBColor(0, 255, 0),
            new CalibrationProfile.RGBColor(15, 235, 12)
        );
        mapping.blue = new CalibrationProfile.ColorMap(
            new CalibrationProfile.RGBColor(0, 0, 255),
            new CalibrationProfile.RGBColor(8, 5, 240)
        );
        mapping.white = new CalibrationProfile.ColorMap(
            new CalibrationProfile.RGBColor(255, 255, 255),
            new CalibrationProfile.RGBColor(248, 250, 245)
        );
        mapping.magenta = new CalibrationProfile.ColorMap(
            new CalibrationProfile.RGBColor(255, 0, 255),
            new CalibrationProfile.RGBColor(235, 15, 230)
        );
        profile.setColorMapping(mapping);
        
        profile.setQuality(new CalibrationProfile.QualityMetrics(185.7f, 303.06f, 73.37f));
        
        String json = profile.toJson();
        assertNotNull(json);
        assertTrue(json.contains("\"version\":\"1.0\""));
        assertTrue(json.contains("\"model\":\"Canon Pixma iP4000\""));
        assertTrue(json.contains("\"type\":\"inkjet\""));
        
        CalibrationProfile deserialized = CalibrationProfile.fromJson(json);
        assertNotNull(deserialized);
        assertEquals("Canon Pixma iP4000", deserialized.getPrinter().model);
        assertEquals("inkjet", deserialized.getPrinter().type);
        assertEquals(240, deserialized.getColorMapping().red.calibrated.r);
        assertEquals(303.06f, deserialized.getQuality().redMagentaSeparation, 0.01f);
    }
    
    @Test
    public void testProfileId() {
        CalibrationProfile profile = new CalibrationProfile();
        profile.setPrinter(new CalibrationProfile.PrinterInfo("Canon Pixma iP4000", "inkjet", ""));
        
        String id = profile.getId();
        assertEquals("canon_pixma_ip4000", id);
    }
}
