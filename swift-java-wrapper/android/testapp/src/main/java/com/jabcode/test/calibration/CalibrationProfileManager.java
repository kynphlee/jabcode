package com.jabcode.test.calibration;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CalibrationProfileManager {
    private static final String TAG = "CalibrationProfileMgr";
    private static final String PREFS_NAME = "jabcode_calibration";
    private static final String KEY_ACTIVE_PROFILE = "active_profile_id";
    private static final String PROFILES_DIR = "calibration_profiles";
    
    private Context context;
    private SharedPreferences prefs;
    private CalibrationProfile activeProfile;
    
    public CalibrationProfileManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadActiveProfile();
    }
    
    public void saveProfile(CalibrationProfile profile) throws IOException, JSONException {
        File profilesDir = getProfilesDirectory();
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
        }
        
        String profileId = profile.getId();
        File profileFile = new File(profilesDir, profileId + ".json");
        
        try (FileWriter writer = new FileWriter(profileFile)) {
            writer.write(profile.toJson());
        }
        
        Log.i(TAG, "Saved calibration profile: " + profileId);
    }
    
    public CalibrationProfile loadProfile(String profileId) throws IOException, JSONException {
        File profileFile = new File(getProfilesDirectory(), profileId + ".json");
        if (!profileFile.exists()) {
            throw new IOException("Profile not found: " + profileId);
        }
        
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(profileFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
        }
        
        CalibrationProfile profile = CalibrationProfile.fromJson(json.toString());
        Log.i(TAG, "Loaded calibration profile: " + profileId);
        return profile;
    }
    
    public List<CalibrationProfile> listProfiles() {
        List<CalibrationProfile> profiles = new ArrayList<>();
        File profilesDir = getProfilesDirectory();
        
        if (!profilesDir.exists()) {
            return profiles;
        }
        
        File[] files = profilesDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try {
                    StringBuilder json = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            json.append(line);
                        }
                    }
                    profiles.add(CalibrationProfile.fromJson(json.toString()));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load profile: " + file.getName(), e);
                }
            }
        }
        
        Log.i(TAG, "Found " + profiles.size() + " calibration profiles");
        return profiles;
    }
    
    public void deleteProfile(String profileId) {
        File profileFile = new File(getProfilesDirectory(), profileId + ".json");
        if (profileFile.exists()) {
            if (profileFile.delete()) {
                Log.i(TAG, "Deleted calibration profile: " + profileId);
                
                if (profileId.equals(getActiveProfileId())) {
                    setActiveProfile(null);
                }
            } else {
                Log.w(TAG, "Failed to delete profile: " + profileId);
            }
        }
    }
    
    public void setActiveProfile(CalibrationProfile profile) {
        this.activeProfile = profile;
        
        if (profile != null) {
            prefs.edit().putString(KEY_ACTIVE_PROFILE, profile.getId()).apply();
            Log.i(TAG, "Set active profile: " + profile.getId());
        } else {
            prefs.edit().remove(KEY_ACTIVE_PROFILE).apply();
            Log.i(TAG, "Cleared active profile");
        }
    }
    
    public CalibrationProfile getActiveProfile() {
        return activeProfile;
    }
    
    public String getActiveProfileId() {
        return prefs.getString(KEY_ACTIVE_PROFILE, null);
    }
    
    public boolean hasActiveProfile() {
        return activeProfile != null;
    }
    
    private void loadActiveProfile() {
        String profileId = getActiveProfileId();
        if (profileId != null) {
            try {
                activeProfile = loadProfile(profileId);
                Log.i(TAG, "Loaded active profile: " + profileId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load active profile", e);
                prefs.edit().remove(KEY_ACTIVE_PROFILE).apply();
            }
        }
    }
    
    private File getProfilesDirectory() {
        return new File(context.getFilesDir(), PROFILES_DIR);
    }
    
    public String exportProfile(CalibrationProfile profile) throws JSONException {
        return profile.toJson();
    }
    
    public CalibrationProfile importProfile(String jsonString) throws JSONException {
        return CalibrationProfile.fromJson(jsonString);
    }
    
    public static class ProfileSummary {
        public String id;
        public String printerModel;
        public String printerType;
        public String qualityLevel;
        public float minSeparation;
        public long created;
        
        public ProfileSummary(CalibrationProfile profile) {
            this.id = profile.getId();
            this.printerModel = profile.getPrinter().model;
            this.printerType = profile.getPrinter().type;
            this.qualityLevel = profile.getQuality().getQualityLevel();
            this.minSeparation = profile.getQuality().minSeparation;
            this.created = profile.getCreated().getTime();
        }
        
        @Override
        public String toString() {
            return printerModel + " (" + qualityLevel + ")";
        }
    }
    
    public List<ProfileSummary> getProfileSummaries() {
        List<ProfileSummary> summaries = new ArrayList<>();
        for (CalibrationProfile profile : listProfiles()) {
            summaries.add(new ProfileSummary(profile));
        }
        return summaries;
    }
}
