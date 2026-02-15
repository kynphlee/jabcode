package com.jabcode.test.calibration;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.jabcode.test.R;
import org.json.JSONException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ProfileManagerActivity extends AppCompatActivity {
    private static final String TAG = "ProfileManager";
    
    private CalibrationProfileManager profileManager;
    private RecyclerView profileList;
    private ProfileAdapter adapter;
    private TextView emptyView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_manager);
        setTitle("Calibration Profiles");
        
        profileManager = new CalibrationProfileManager(this);
        
        profileList = findViewById(R.id.profile_list);
        emptyView = findViewById(R.id.empty_view);
        
        profileList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProfileAdapter();
        profileList.setAdapter(adapter);
        
        findViewById(R.id.btn_new_calibration).setOnClickListener(v -> 
            startActivity(new Intent(this, CalibrationActivity.class)));
        
        loadProfiles();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadProfiles();
    }
    
    private void loadProfiles() {
        List<CalibrationProfileManager.ProfileSummary> summaries = 
            profileManager.getProfileSummaries();
        
        if (summaries.isEmpty()) {
            profileList.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            profileList.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            adapter.setProfiles(summaries);
        }
    }
    
    private class ProfileAdapter extends RecyclerView.Adapter<ProfileViewHolder> {
        private List<CalibrationProfileManager.ProfileSummary> profiles;
        
        public void setProfiles(List<CalibrationProfileManager.ProfileSummary> profiles) {
            this.profiles = profiles;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calibration_profile, parent, false);
            return new ProfileViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
            CalibrationProfileManager.ProfileSummary profile = profiles.get(position);
            holder.bind(profile);
        }
        
        @Override
        public int getItemCount() {
            return profiles != null ? profiles.size() : 0;
        }
    }
    
    private class ProfileViewHolder extends RecyclerView.ViewHolder {
        private TextView printerText;
        private TextView qualityText;
        private TextView dateText;
        private Button activateButton;
        private Button deleteButton;
        private Button exportButton;
        
        public ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            printerText = itemView.findViewById(R.id.printer_text);
            qualityText = itemView.findViewById(R.id.quality_text);
            dateText = itemView.findViewById(R.id.date_text);
            activateButton = itemView.findViewById(R.id.btn_activate);
            deleteButton = itemView.findViewById(R.id.btn_delete);
            exportButton = itemView.findViewById(R.id.btn_export);
        }
        
        public void bind(CalibrationProfileManager.ProfileSummary profile) {
            printerText.setText(profile.printerModel);
            qualityText.setText(String.format("%s (%.1f min sep)", 
                profile.qualityLevel, profile.minSeparation));
            
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            dateText.setText(sdf.format(profile.created));
            
            String activeId = profileManager.getActiveProfileId();
            boolean isActive = profile.id.equals(activeId);
            
            activateButton.setText(isActive ? "Active" : "Activate");
            activateButton.setEnabled(!isActive);
            
            activateButton.setOnClickListener(v -> activateProfile(profile));
            deleteButton.setOnClickListener(v -> confirmDelete(profile));
            exportButton.setOnClickListener(v -> exportProfile(profile));
        }
    }
    
    private void activateProfile(CalibrationProfileManager.ProfileSummary summary) {
        try {
            CalibrationProfile profile = profileManager.loadProfile(summary.id);
            profileManager.setActiveProfile(profile);
            
            Toast.makeText(this, "Profile activated: " + summary.printerModel, 
                Toast.LENGTH_SHORT).show();
            adapter.notifyDataSetChanged();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to activate profile", e);
            Toast.makeText(this, "Activation failed: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    private void confirmDelete(CalibrationProfileManager.ProfileSummary profile) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Profile")
            .setMessage("Delete calibration profile for " + profile.printerModel + "?")
            .setPositiveButton("Delete", (dialog, which) -> deleteProfile(profile))
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void deleteProfile(CalibrationProfileManager.ProfileSummary profile) {
        profileManager.deleteProfile(profile.id);
        Toast.makeText(this, "Profile deleted", Toast.LENGTH_SHORT).show();
        loadProfiles();
    }
    
    private void exportProfile(CalibrationProfileManager.ProfileSummary summary) {
        try {
            CalibrationProfile profile = profileManager.loadProfile(summary.id);
            String json = profileManager.exportProfile(profile);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_TEXT, json);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, 
                "JABCode Calibration Profile - " + profile.getPrinter().model);
            
            startActivity(Intent.createChooser(shareIntent, "Export Profile"));
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to export profile", e);
            Toast.makeText(this, "Export failed: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
}
