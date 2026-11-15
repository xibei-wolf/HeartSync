package com.heartsync.app.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.ChipGroup;
import com.heartsync.app.R;
import com.heartsync.app.adapter.FirstAidAdapter;
import com.heartsync.app.databinding.ActivityFirstaidBinding;
import com.heartsync.app.model.FirstAidItem;
import com.heartsync.app.net.ServerApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 校园急救知识列表页。
 *
 * <p>数据来源优先读取本地 assets 的急救知识（保证离线可用），
 * 随后尝试请求服务器接口，成功则替换为服务器数据。</p>
 *
 * <p>技术点对应：RecyclerView 列表与适配器、JSON 数据解析、
 * 本地文件读取（assets）、搜索与分类筛选。</p>
 */
public class FirstAidActivity extends AppCompatActivity {

    /** 本地急救知识数据文件 */
    private static final String ASSET_FIRST_AID = "first_aid.json";

    private ActivityFirstaidBinding binding;

    /** 列表适配器 */
    private FirstAidAdapter adapter;

    /** 全部急救条目 */
    private final List<FirstAidItem> allItems = new ArrayList<>();

    /** 当前的搜索关键词 */
    private String keyword = "";

    /** 当前选中的分类，空表示全部 */
    private String currentCategory = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFirstaidBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupRecyclerView();
        setupListeners();

        // 先展示本地数据，页面打开即可用
        loadLocalData();
        // 再尝试拉取服务器数据，成功则覆盖
        fetchRemoteData();
    }

    /**
     * 初始化列表。
     */
    private void setupRecyclerView() {
        binding.rvAid.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FirstAidAdapter((item, position) -> openDetail(item));
        binding.rvAid.setAdapter(adapter);
    }

    /**
     * 绑定控件事件。
     */
    private void setupListeners() {
        binding.ivBack.setOnClickListener(v -> finish());

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                keyword = s.toString().trim();
                binding.ivClearSearch.setVisibility(
                        keyword.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilter();
            }
        });

        binding.ivClearSearch.setOnClickListener(v -> binding.etSearch.setText(""));

        binding.cgCategory.setOnCheckedChangeListener(new ChipGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(ChipGroup group, int checkedId) {
                if (checkedId == R.id.chipCatAll) {
                    currentCategory = "";
                } else if (checkedId == R.id.chipCat1) {
                    currentCategory = "意外伤害";
                } else if (checkedId == R.id.chipCat2) {
                    currentCategory = "急症处置";
                } else if (checkedId == R.id.chipCat3) {
                    currentCategory = "常见不适";
                } else if (checkedId == R.id.chipCat4) {
                    currentCategory = "心理急救";
                } else {
                    currentCategory = "";
                }
                applyFilter();
            }
        });
    }

    /**
     * 打开急救详情页，通过 Intent 传递条目对象。
     */
    private void openDetail(FirstAidItem item) {
        if (item == null) {
            return;
        }
        Intent intent = new Intent(this, FirstAidDetailActivity.class);
        intent.putExtra(FirstAidDetailActivity.EXTRA_AID, item);
        startActivity(intent);
    }

    /**
     * 读取本地 assets 中的急救知识。
     */
    private void loadLocalData() {
        try (InputStream is = getAssets().open(ASSET_FIRST_AID);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.getJSONArray("items");
            List<FirstAidItem> items = ServerApi.parseFirstAidArray(arr);
            if (!items.isEmpty()) {
                allItems.clear();
                allItems.addAll(items);
                applyFilter();
            }
        } catch (Exception e) {
            // 本地数据读取失败时保持空列表，不影响页面其它功能
            applyFilter();
        }
    }

    /**
     * 拉取服务器急救知识，失败时保留本地数据。
     */
    private void fetchRemoteData() {
        ServerApi.fetchFirstAid(this, new ServerApi.ApiCallback<List<FirstAidItem>>() {
            @Override
            public void onSuccess(List<FirstAidItem> data) {
                if (data == null || data.isEmpty()) {
                    return;
                }
                allItems.clear();
                allItems.addAll(data);
                applyFilter();
            }

            @Override
            public void onFailure(String message) {
                // 网络失败时继续使用本地数据
            }
        });
    }

    /**
     * 按搜索关键词与分类筛选条目，并刷新列表。
     */
    private void applyFilter() {
        List<FirstAidItem> filtered = new ArrayList<>();
        String lower = keyword.toLowerCase(Locale.CHINA);
        for (FirstAidItem item : allItems) {
            if (item == null) {
                continue;
            }
            if (!currentCategory.isEmpty() && !currentCategory.equals(item.getCategory())) {
                continue;
            }
            if (!lower.isEmpty() && !matchKeyword(item, lower)) {
                continue;
            }
            filtered.add(item);
        }
        adapter.setData(filtered);

        boolean empty = filtered.isEmpty();
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvAid.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * 判断条目是否命中关键词（标题、分类、症状）。
     */
    private boolean matchKeyword(FirstAidItem item, String lower) {
        return contains(item.getTitle(), lower)
                || contains(item.getCategory(), lower)
                || contains(item.getSymptom(), lower);
    }

    /**
     * 忽略大小写地判断文本是否包含关键词。
     */
    private static boolean contains(String text, String lower) {
        return text != null && text.toLowerCase(Locale.CHINA).contains(lower);
    }
}
