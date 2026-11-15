package com.heartsync.app.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.heartsync.app.R;
import com.heartsync.app.databinding.ActivityLoginBinding;
import com.heartsync.app.db.HeartSyncDb;
import com.heartsync.app.model.User;
import com.heartsync.app.util.CommonUtils;
import com.heartsync.app.util.PrefUtils;

/**
 * 登录 / 注册页。
 *
 * <p>技术点对应：Activity 页面跳转与传值、数据存储（SQLite 用户表 + SharedPreferences 会话）。
 * 注册与登录共用同一页面，通过模式切换。</p>
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private HeartSyncDb db;
    /** 当前是否为注册模式 */
    private boolean registerMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = HeartSyncDb.getInstance(this);

        binding.btnLogin.setOnClickListener(v -> handleLogin());
        binding.tvToggleMode.setOnClickListener(v -> toggleMode());
    }

    /**
     * 切换登录 / 注册模式。
     */
    private void toggleMode() {
        registerMode = !registerMode;
        if (registerMode) {
            binding.tvLoginTitle.setText(R.string.register_title);
            binding.tvLoginSubtitle.setText(R.string.register_subtitle);
            binding.btnLogin.setText("注册并登录");
            binding.tvToggleMode.setText("已有账号？返回登录");
            binding.tilClass.setVisibility(android.view.View.VISIBLE);
        } else {
            binding.tvLoginTitle.setText(R.string.login_title);
            binding.tvLoginSubtitle.setText(R.string.login_subtitle);
            binding.btnLogin.setText(R.string.login_btn);
            binding.tvToggleMode.setText(R.string.login_to_register);
            binding.tilClass.setVisibility(android.view.View.GONE);
        }
    }

    /**
     * 处理登录 / 注册点击事件。
     */
    private void handleLogin() {
        String account = binding.etAccount.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String className = binding.etClass.getText().toString().trim();

        if (TextUtils.isEmpty(account)) {
            CommonUtils.toast(this, getString(R.string.login_err_account_empty));
            return;
        }
        if (password.length() < 6) {
            CommonUtils.toast(this, getString(R.string.login_err_password_short));
            return;
        }

        if (registerMode) {
            doRegister(account, password, className);
        } else {
            doLogin(account, password);
        }
    }

    /**
     * 注册新用户：学号作为唯一账号，密码以 SHA-256 哈希存储。
     */
    private void doRegister(String account, String password, String className) {
        if (db.findUserByAccount(account) != null) {
            CommonUtils.toast(this, getString(R.string.login_err_account_exists));
            return;
        }
        User user = new User();
        user.setAccount(account);
        // 昵称默认取学号后 4 位，不足则用学号本身
        String nickname = account.length() > 4 ? account.substring(account.length() - 4) : account;
        user.setNickname(nickname);
        user.setClassName(className.isEmpty() ? "软件2303班" : className);
        user.setPasswordHash(CommonUtils.sha256(password));
        user.setAvatarColor(account.hashCode() % 4);

        if (db.registerUser(user)) {
            PrefUtils.saveLogin(this, user);
            CommonUtils.toast(this, "注册成功，欢迎使用心晴校园");
            goMain();
        } else {
            CommonUtils.toast(this, getString(R.string.common_failed));
        }
    }

    /**
     * 登录已有账号。
     */
    private void doLogin(String account, String password) {
        User user = db.findUserByAccount(account);
        if (user == null) {
            CommonUtils.toast(this, getString(R.string.login_err_account_not_exists));
            return;
        }
        if (!user.getPasswordHash().equals(CommonUtils.sha256(password))) {
            CommonUtils.toast(this, getString(R.string.login_err_password_wrong));
            return;
        }
        PrefUtils.saveLogin(this, user);
        CommonUtils.toast(this, getString(R.string.common_success));
        goMain();
    }

    /**
     * 跳转主界面。
     */
    private void goMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
