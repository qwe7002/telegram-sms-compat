package com.qwe7002.telegram_sms_compat;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;

import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.Context.MODE_PRIVATE;

public class sms_send_receiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        final String TAG = "sms_send_receiver";
        Log.d(TAG, "Receive action: " + intent.getAction());
        Bundle extras = intent.getExtras();
        assert extras != null;
        context.getApplicationContext().unregisterReceiver(this);
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, SMS send receiver is deactivated.");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        final message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        String request_uri = public_func.get_url(bot_token, "sendMessage");

        long message_id = Long.parseLong(Objects.requireNonNull(extras.getString("message_id")));
        if (message_id != -1) {
            Log.d(TAG, "Find the message_id and switch to edit mode.");
            request_uri = public_func.get_url(bot_token, "editMessageText");
            request_body.message_id = message_id;
        }
        String result_status = "Unknown";
        switch (getResultCode()) {
            case Activity.RESULT_OK:
                result_status = context.getString(R.string.success);
                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                result_status = context.getString(R.string.send_failed);
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                result_status = context.getString(R.string.airplane_mode);
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                result_status = context.getString(R.string.no_network);
                break;
        }
        request_body.text = extras.getString("message_text") + "\n" + context.getString(R.string.status) + result_status;
        String request_body_raw = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(public_func.JSON, request_body_raw);
        OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        final String error_head = "Send SMS status failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
                public_func.write_log(context, error_head + e.getMessage());
                public_func.send_fallback_sms(context, request_body.text);
                public_func.add_resend_loop(context, request_body.text);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.code() != 200) {
                    assert response.body() != null;
                    public_func.write_log(context, error_head + response.code() + " " + response.body().string());
                    public_func.add_resend_loop(context, request_body.text);

                }
            }
        });
    }
}
