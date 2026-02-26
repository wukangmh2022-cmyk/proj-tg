package com.glocalvision.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText requestInput = findViewById(R.id.requestInput);
        EditText messagesInput = findViewById(R.id.messagesInput);
        TextView outputView = findViewById(R.id.outputView);
        Button analyzeButton = findViewById(R.id.analyzeButton);

        requestInput.setText("监控XXX土狗交流群，搜集2年内入场出场信号，统计累计收益和回撤");
        messagesInput.setText(
                "2025-01-12 09:20 KOL_A BTCUSDT buy at 41000\n" +
                "2025-01-19 16:30 KOL_A BTCUSDT take profit at 43500\n" +
                "2025-03-01 10:00 KOL_A ETHUSDT 建仓 2500\n" +
                "2025-03-06 20:10 KOL_A ETHUSDT 止损 2320\n" +
                "2025-05-10 12:00 KOL_A SOLUSDT 入场 145\n" +
                "2025-05-22 18:15 KOL_A SOLUSDT 出场 178"
        );

        analyzeButton.setOnClickListener(v -> {
            String request = requestInput.getText().toString();
            String messages = messagesInput.getText().toString();
            String report = SignalAnalyzer.analyze(messages, request);
            outputView.setText(report);
        });

        outputView.setText(SignalAnalyzer.analyze(messagesInput.getText().toString(), requestInput.getText().toString()));
    }
}
