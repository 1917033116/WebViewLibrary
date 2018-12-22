package com.webviewlib;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.example.webviewlibrary.R;


/**
 * 提示框
 */
public class DialogCancelConfirm extends Dialog {

    public static class Builder {
        private Context context;
        private String title;
        private String message;
        private String leftBtnText = "取消";
        private String rightBtnText = "确定";
        private String singleBtnText = "确定";
        private OnDoubleBtnClickListener doubleBtnClickListener;
        private OnSingleBtnClickListener singClick;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder showTitle() {
            this.title = "公告";
            return this;
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder setBtnText(String leftBtnText, String rightBtnText) {
            this.leftBtnText = leftBtnText;
            this.rightBtnText = rightBtnText;
            return this;
        }

        public Builder setSingleBtnText(String singleBtn) {
            this.leftBtnText = singleBtn;
            return this;
        }

        public Builder setDoubleBtnClickListener(OnDoubleBtnClickListener opClickListener) {
            this.doubleBtnClickListener = opClickListener;
            return this;
        }

        public Builder setSingClick(OnSingleBtnClickListener singClick) {
            this.singClick = singClick;
            return this;
        }

        public DialogCancelConfirm build() {
            DialogCancelConfirm dialog = new DialogCancelConfirm(context);
            if (title != null) dialog.setTitle(title);
            if (message != null) dialog.setMessage(message);
            if (doubleBtnClickListener != null) {
                dialog.setOperationListener(leftBtnText, rightBtnText, doubleBtnClickListener);
            } else if (singClick != null) {
                dialog.setSingleBtnClickListener(singleBtnText, singClick);
            } else {
                dialog.setSingleBtnClickListener(singleBtnText, new OnSingleBtnClickListener() {
                    @Override
                    public void onClick(DialogCancelConfirm dialog) {
                        dialog.dismiss();
                    }
                });
            }
            return dialog;
        }
    }


    public void setTitle(CharSequence message) {
        TextView tvTitle = findViewById(R.id.tvTitle);
        findViewById(R.id.tvTitle).setVisibility(View.VISIBLE);
        findViewById(R.id.titleDivider).setVisibility(View.VISIBLE);
        tvTitle.setVisibility(View.VISIBLE);
        tvTitle.setText(message);
    }

    public void setMessage(CharSequence message) {
        ((TextView) findViewById(R.id.dialogText)).setText(message);
    }

    public void setSingleBtnClickListener(String btnText, final OnSingleBtnClickListener listener) {
        TextView rightBtn = findViewById(R.id.dialogRightBtn);
        findViewById(R.id.dialogLeftBtn).setVisibility(View.GONE);
        findViewById(R.id.left_right_divider).setVisibility(View.GONE);
        rightBtn.setText(btnText);
        rightBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClick(DialogCancelConfirm.this);
            }
        });
    }

    public void setOperationListener(CharSequence left, CharSequence right,
                                     final OnDoubleBtnClickListener listener) {
        TextView tvLeft = findViewById(R.id.dialogLeftBtn);
        tvLeft.setText(left);
        tvLeft.setVisibility(View.VISIBLE);
        findViewById(R.id.left_right_divider).setVisibility(View.VISIBLE);
        TextView tvRight = findViewById(R.id.dialogRightBtn);
        tvRight.setText(right);
        tvLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClick(DialogCancelConfirm.this, true);
            }
        });
        tvRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClick(DialogCancelConfirm.this, false);
            }
        });
    }

    public DialogCancelConfirm(Context context) {
        super(context, R.style.CustomDialog);
        setContentView(R.layout.dialog_cancel_confirm);
    }
}