package com.vypeensoft.treenode;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.navigation.NavigationView;
import com.vypeensoft.treenode.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private ActivityMainBinding binding;
    private JsonStorageManager storageManager;
    private MasterTreeAdapter adapter;
    private final List<TreeDocument> treeDocuments = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.applyTheme(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        storageManager = new JsonStorageManager(this);
        checkAndRequestStoragePermissions();

        // Setup Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Tree Notes");
        }

        // Setup Drawer
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        binding.navView.setNavigationItemSelectedListener(this);
        binding.navView.setCheckedItem(R.id.nav_home);

        // Setup RecyclerView
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MasterTreeAdapter(treeDocuments, new MasterTreeAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(TreeDocument document) {
                Intent intent = new Intent(MainActivity.this, TreeListActivity.class);
                intent.putExtra("document_id", document.getName());
                startActivity(intent);
            }

            @Override
            public void onDeleteClick(TreeDocument document) {
                showDeleteConfirmationDialog(document);
            }

            @Override
            public void onRenameClick(TreeDocument document) {
                showRenameDialog(document);
            }
        });
        binding.recyclerView.setAdapter(adapter);

        // Setup FAB
        binding.fabAddTree.setOnClickListener(v -> showAddTreeDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.navView.setCheckedItem(R.id.nav_home);
        loadMasterTrees();
    }

    private void loadMasterTrees() {
        treeDocuments.clear();
        List<TreeDocument> loaded = storageManager.loadAllMasterTrees();
        if (loaded != null) {
            // Sort alphabetically as requested
            loaded.sort((d1, d2) -> {
                if (d1.getName() == null) return -1;
                if (d2.getName() == null) return 1;
                return d1.getName().compareToIgnoreCase(d2.getName());
            });
            treeDocuments.addAll(loaded);
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (treeDocuments.isEmpty()) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.recyclerView.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showAddTreeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Master Tree");

        final EditText input = new EditText(this);
        input.setHint("Tree Name");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        
        // Add padding to EditText
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = getResources().getDimensionPixelSize(R.dimen.dialog_margin);
        params.rightMargin = getResources().getDimensionPixelSize(R.dimen.dialog_margin);
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                String id = UUID.randomUUID().toString();
                TreeDocument doc = new TreeDocument(id, name);
                storageManager.saveTreeDocument(doc);
                loadMasterTrees();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        
        // Automatically focus and show keyboard
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        dialog.show();
    }

    private void showRenameDialog(TreeDocument document) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Tree");

        final EditText input = new EditText(this);
        input.setText(document.getName());
        input.setSelection(input.getText().length());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = getResources().getDimensionPixelSize(R.dimen.dialog_margin);
        params.rightMargin = getResources().getDimensionPixelSize(R.dimen.dialog_margin);
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("Rename", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                storageManager.renameTreeDocument(document.getName(), name);
                loadMasterTrees();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();

        // Automatically focus and show keyboard
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        dialog.show();
    }

    private void showDeleteConfirmationDialog(TreeDocument document) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Tree")
                .setMessage("Are you sure you want to delete '" + document.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    storageManager.deleteTreeDocument(document.getName());
                    loadMasterTrees();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.nav_help) {
            startActivity(new Intent(this, HelpActivity.class));
        } else if (id == R.id.nav_about) {
            startActivity(new Intent(this, AboutActivity.class));
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void checkAndRequestStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                        .setTitle("Storage Permission Required")
                        .setMessage("Tree Notes requires 'All Files Access' to read and save your hierarchical tree files to /sdcard/Vypeensoft/Tree_Notes/. Please grant this permission on the next screen.")
                        .setPositiveButton("Grant Permission", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            } catch (Exception e) {
                                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        } else {
            String[] permissions = new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            boolean allGranted = true;
            for (String perm : permissions) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                androidx.core.app.ActivityCompat.requestPermissions(this, permissions, 100);
            }
        }
    }
}

