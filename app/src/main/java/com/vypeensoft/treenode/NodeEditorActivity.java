package com.vypeensoft.treenode;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.vypeensoft.treenode.databinding.ActivityNodeEditorBinding;
import jp.wasabeef.richeditor.RichEditor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public class NodeEditorActivity extends AppCompatActivity {

    private ActivityNodeEditorBinding binding;
    private JsonStorageManager storageManager;
    private String documentId;
    private String nodeId;
    
    private TreeDocument document;
    private TreeNode targetNode;
    
    private boolean isBold = false;
    private boolean isItalic = false;

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        saveAndInsertImage(imageUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNodeEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        storageManager = new JsonStorageManager(this);

        // Retrieve Intent Extras
        documentId = getIntent().getStringExtra("document_id");
        nodeId = getIntent().getStringExtra("node_id");

        // Load document
        document = storageManager.loadTreeDocument(documentId);
        if (document == null) {
            finish();
            return;
        }

        // Find target node recursively
        targetNode = findNodeById(document.getRootNodes(), nodeId);
        if (targetNode == null) {
            finish();
            return;
        }

        // Setup Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Edit Note");
        }

        // Populate Views
        binding.editNodeTitle.setText(targetNode.getTitle());
        
        // Setup RichEditor
        binding.editNodeContent.setEditorHeight(200);
        binding.editNodeContent.setEditorFontSize(16);
        binding.editNodeContent.setPadding(10, 10, 10, 10);
        binding.editNodeContent.setPlaceholder("Start typing your notes here...");
        
        // Enable file access for local images
        binding.editNodeContent.getSettings().setAllowFileAccess(true);
        binding.editNodeContent.getSettings().setAllowContentAccess(true);
        binding.editNodeContent.getSettings().setAllowFileAccessFromFileURLs(true);
        binding.editNodeContent.getSettings().setAllowUniversalAccessFromFileURLs(true);
        
        if (targetNode.getContent() != null && !targetNode.getContent().isEmpty()) {
            binding.editNodeContent.setHtml(targetNode.getContent());
        }

        // Toolbar Buttons
        binding.actionBold.setOnClickListener(v -> {
            isBold = !isBold;
            binding.editNodeContent.setBold();
            binding.actionBold.setBackgroundColor(isBold ? Color.LTGRAY : Color.TRANSPARENT);
        });

        binding.actionItalic.setOnClickListener(v -> {
            isItalic = !isItalic;
            binding.editNodeContent.setItalic();
            binding.actionItalic.setBackgroundColor(isItalic ? Color.LTGRAY : Color.TRANSPARENT);
        });

        binding.actionInsertImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        if (result == null || result.isEmpty()) {
            result = UUID.randomUUID().toString() + ".jpg";
        }
        return result;
    }

    private void saveAndInsertImage(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return;

            File baseDir = storageManager.getResolvedStorageDir();
            File imagesDir = new File(baseDir, "attachments");
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }

            String originalName = getFileName(uri);
            String nameWithoutExt = originalName;
            String ext = "";
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex > 0) {
                nameWithoutExt = originalName.substring(0, dotIndex);
                ext = originalName.substring(dotIndex);
            }

            File localFile = new File(imagesDir, originalName);
            int counter = 1;
            while (localFile.exists()) {
                localFile = new File(imagesDir, nameWithoutExt + "_" + counter + ext);
                counter++;
            }

            FileOutputStream outputStream = new FileOutputStream(localFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            outputStream.close();

            String localPath = "file://" + localFile.getAbsolutePath();
            binding.editNodeContent.insertImage(localPath, "image", 320);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNodeChanges();
    }

    private void saveNodeChanges() {
        if (document != null && targetNode != null) {
            String newTitle = binding.editNodeTitle.getText().toString().trim();
            String newContent = binding.editNodeContent.getHtml();

            // Sane fallback for empty title
            if (newTitle.isEmpty()) {
                newTitle = "Untitled Node";
            }

            targetNode.setTitle(newTitle);
            if (newContent != null) {
                targetNode.setContent(newContent);
            }

            storageManager.saveTreeDocument(document);
        }
    }

    private TreeNode findNodeById(List<TreeNode> list, String id) {
        if (list == null) return null;
        for (TreeNode node : list) {
            if (node.getId().equals(id)) {
                return node;
            }
            TreeNode found = findNodeById(node.getChildren(), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Save and return
            saveNodeChanges();
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

