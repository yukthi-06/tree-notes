package com.vypeensoft.treenode;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.vypeensoft.treenode.databinding.ActivityTreeListBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TreeListActivity extends AppCompatActivity {

    private ActivityTreeListBinding binding;
    private JsonStorageManager storageManager;
    private String documentId;
    private String parentNodeId;
    private String breadcrumbPath;
    
    private TreeDocument document;
    private TreeNode parentNode; // Null if root level
    
    private TreeNodeAdapter adapter;
    private final List<TreeNode> currentNodesList = new ArrayList<>();
    private final List<TreeNode> filteredNodesList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTreeListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        storageManager = new JsonStorageManager(this);

        // Retrieve Intent Extras
        documentId = getIntent().getStringExtra("document_id");
        parentNodeId = getIntent().getStringExtra("parent_node_id");
        breadcrumbPath = getIntent().getStringExtra("breadcrumb_path");

        // Load document
        document = storageManager.loadTreeDocument(documentId);
        if (document == null) {
            finish();
            return;
        }

        if (breadcrumbPath == null) {
            breadcrumbPath = document.getName();
        }

        // Setup Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (parentNodeId == null) {
                getSupportActionBar().setTitle(document.getName());
            } else {
                parentNode = findNodeById(document.getRootNodes(), parentNodeId);
                if (parentNode != null) {
                    getSupportActionBar().setTitle(parentNode.getTitle());
                }
            }
        }

        // Render Breadcrumbs
        binding.textBreadcrumb.setText(breadcrumbPath);

        // Setup RecyclerView
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TreeNodeAdapter(filteredNodesList, new TreeNodeAdapter.OnNodeClickListener() {
            @Override
            public void onNodeClick(TreeNode node) {
                if (node.hasChildren()) {
                    // Open next level
                    Intent intent = new Intent(TreeListActivity.this, TreeListActivity.class);
                    intent.putExtra("document_id", documentId);
                    intent.putExtra("parent_node_id", node.getId());
                    intent.putExtra("breadcrumb_path", buildBreadcrumbPath(node));
                    startActivity(intent);
                } else {
                    // Open editor
                    openEditor(node);
                }
            }

            @Override
            public void onAddChild(TreeNode node) {
                showAddNodeDialog(node);
            }

            @Override
            public void onEdit(TreeNode node) {
                openEditor(node);
            }

            @Override
            public void onDelete(TreeNode node) {
                showDeleteNodeDialog(node);
            }
        });
        binding.recyclerView.setAdapter(adapter);

        // Setup FAB
        binding.fabAddNode.setOnClickListener(v -> showAddNodeDialog(null));

        // Setup Search
        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterNodes(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
    }

    private void refreshData() {
        // Re-load to get latest updates (especially from editor screen)
        document = storageManager.loadTreeDocument(documentId);
        if (document == null) {
            finish();
            return;
        }

        currentNodesList.clear();
        if (parentNodeId == null) {
            currentNodesList.addAll(document.getRootNodes());
        } else {
            parentNode = findNodeById(document.getRootNodes(), parentNodeId);
            if (parentNode != null) {
                currentNodesList.addAll(parentNode.getChildren());
            }
        }
        
        filterNodes(binding.editSearch.getText().toString());
    }

    private void filterNodes(String query) {
        filteredNodesList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredNodesList.addAll(currentNodesList);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (TreeNode node : currentNodesList) {
                searchRecursively(node, lowerQuery, filteredNodesList);
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void searchRecursively(TreeNode node, String query, List<TreeNode> results) {
        if (node == null) return;
        
        boolean matchTitle = node.getTitle() != null && node.getTitle().toLowerCase().contains(query);
        boolean matchContent = node.getContent() != null && node.getContent().toLowerCase().contains(query);
        
        if (matchTitle || matchContent) {
            results.add(node);
        }
        
        if (node.getChildren() != null) {
            for (TreeNode child : node.getChildren()) {
                searchRecursively(child, query, results);
            }
        }
    }

    private void updateEmptyState() {
        if (filteredNodesList.isEmpty()) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.recyclerView.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void moveNode(int position, int direction) {
        // Find corresponding nodes in currentNodesList
        TreeNode nodeToMove = filteredNodesList.get(position);
        int actualIndex = currentNodesList.indexOf(nodeToMove);
        int targetIndex = actualIndex + direction;

        if (targetIndex >= 0 && targetIndex < currentNodesList.size()) {
            Collections.swap(currentNodesList, actualIndex, targetIndex);
            
            // Save document
            storageManager.saveTreeDocument(document);
            refreshData();
        }
    }

    private void showAddNodeDialog(@Nullable TreeNode targetParent) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(targetParent == null ? "Add Node" : "Add Child to " + targetParent.getTitle());

        final EditText input = new EditText(this);
        input.setHint("Node Title");
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

        builder.setPositiveButton("Add", (dialog, which) -> {
            String title = input.getText().toString().trim();
            if (!title.isEmpty()) {
                String id = UUID.randomUUID().toString();
                TreeNode newNode = new TreeNode(id, title, "");
                
                if (targetParent != null) {
                    // Adding child to a specific node
                    targetParent.addChild(newNode);
                } else {
                    // Adding node to current list level
                    if (parentNodeId == null) {
                        document.getRootNodes().add(newNode);
                    } else {
                        if (parentNode != null) {
                            parentNode.addChild(newNode);
                        }
                    }
                }
                
                storageManager.saveTreeDocument(document);
                refreshData();
            }
        });
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();

        // Focus and show keyboard
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

    private void showDeleteNodeDialog(TreeNode node) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Node")
                .setMessage("Are you sure you want to delete '" + node.getTitle() + "' and all of its sub-items?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (parentNodeId == null) {
                        document.getRootNodes().remove(node);
                    } else {
                        if (parentNode != null) {
                            parentNode.removeChild(node);
                        }
                    }
                    storageManager.saveTreeDocument(document);
                    refreshData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openEditor(TreeNode node) {
        Intent intent = new Intent(this, NodeEditorActivity.class);
        intent.putExtra("document_id", documentId);
        intent.putExtra("node_id", node.getId());
        startActivity(intent);
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
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String buildBreadcrumbPath(TreeNode targetNode) {
        List<String> pathSegments = new ArrayList<>();
        pathSegments.add(document.getName());
        findPathSegments(document.getRootNodes(), targetNode.getId(), pathSegments);
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pathSegments.size(); i++) {
            sb.append(pathSegments.get(i));
            if (i < pathSegments.size() - 1) {
                sb.append(" > ");
            }
        }
        return sb.toString();
    }

    private boolean findPathSegments(List<TreeNode> currentList, String targetId, List<String> pathSegments) {
        if (currentList == null) return false;
        for (TreeNode node : currentList) {
            pathSegments.add(node.getTitle());
            if (node.getId().equals(targetId)) {
                return true;
            }
            if (findPathSegments(node.getChildren(), targetId, pathSegments)) {
                return true;
            }
            pathSegments.remove(pathSegments.size() - 1);
        }
        return false;
    }
}

