package com.webviewlib;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;

import com.daimajia.numberprogressbar.NumberProgressBar;
import com.example.webviewlibrary.R;

public class NumProgressDialog extends Dialog {
    static int theme = R.style.BaseDialogTheme;
    static boolean isCancelable = true;
    private NumberProgressBar numberProgressBar;

    public NumProgressDialog(Context context) {
        super(context, theme);
        //设置是否可取消
        setCancelable(isCancelable);
        setCanceledOnTouchOutside(isCancelable);
        setContentView(R.layout.dialog_num_progress);
        numberProgressBar= findViewById(R.id.numberProgressBar);
    }

    public void setProgress(int progress) {
        numberProgressBar.setProgress(progress);
    }

    public void setAction(String actionName, View.OnClickListener listener) {
        Button btn = findViewById(R.id.btnAction);
        btn.setVisibility(View.VISIBLE);
        btn.setText(actionName);
        btn.setOnClickListener(listener);
    }
}
