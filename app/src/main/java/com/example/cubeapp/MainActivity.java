package com.example.cubeapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean stopThread;
    private byte[] buffer;
    private Handler handler;

    private ImageView statusIndicator;
    private ImageView torneiraGif;
    private Button btnLigar;
    private Button btnDesligar;

    // UUID padrão SPP para Bluetooth
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusIndicator = findViewById(R.id.statusIndicator);
        torneiraGif = findViewById(R.id.torneiraGif);
        btnLigar = findViewById(R.id.btnLigar);
        btnDesligar = findViewById(R.id.btnDesligar);
        Button btnSelecionarDispositivo = findViewById(R.id.btnSelecionarDispositivo);

        // Inicializar o adaptador Bluetooth
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth não disponível", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Configurar botão de seleção de dispositivo
        btnSelecionarDispositivo.setOnClickListener(view -> showDeviceSelectionDialog());

        // Configurar botões de Ligar/Desligar
        btnLigar.setOnClickListener(view -> sendData("A"));
        btnDesligar.setOnClickListener(view -> sendData("B"));

        // Handler para atualização da UI quando receber dados do ESP32
        handler = new Handler(msg -> {
            char receivedChar = (char) msg.what;
            if (receivedChar == '#') {
                torneiraGif.setImageResource(R.drawable.imagemgotas); // Mostra GIF da torneira descendo
            } else if (receivedChar == '$') {
                torneiraGif.setImageResource(R.drawable.xdeerro); // Mostra GIF da torneira com X
            }
            return true;
        });
    }

    // Exibe uma lista de dispositivos emparelhados para seleção
    @SuppressLint("InlinedApi")
    private void showDeviceSelectionDialog() {
        // Verificar e solicitar permissões de Bluetooth no Android 12+
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return;
        }

        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Selecione um dispositivo");

            ArrayAdapter<String> deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
            for (BluetoothDevice device : pairedDevices) {
                deviceListAdapter.add(device.getName() + "\n" + device.getAddress());
            }

            builder.setAdapter(deviceListAdapter, (dialog, which) -> {
                String deviceInfo = deviceListAdapter.getItem(which);
                String deviceAddress = deviceInfo.substring(deviceInfo.length() - 17);
                connectToDevice(deviceAddress);
            });

            builder.show();
        } else {
            Toast.makeText(this, "Nenhum dispositivo emparelhado encontrado", Toast.LENGTH_SHORT).show();
        }
    }

    // Conecta ao dispositivo selecionado via Bluetooth
    private void connectToDevice(String deviceAddress) {
        BluetoothDevice device = btAdapter.getRemoteDevice(deviceAddress);

        try {
            // Verificar permissões antes de conectar (Android 12+)
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 1);
                return;
            }

            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            btSocket.connect();
            outputStream = btSocket.getOutputStream();
            inputStream = btSocket.getInputStream();

            // Mudança de status: conectado (imagem Wi-Fi verde)
            statusIndicator.setImageResource(R.drawable.iconebtverde);
            Toast.makeText(this, "Conectado ao " + device.getName(), Toast.LENGTH_SHORT).show();

            // Habilitar botões de Ligar/Desligar
            btnLigar.setEnabled(true);
            btnDesligar.setEnabled(true);

            // Iniciar thread de recepção de dados
            startDataListening();

        } catch (IOException e) {
            Toast.makeText(this, "Falha na conexão", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            try {
                btSocket.close();
            } catch (IOException closeException) {
                closeException.printStackTrace();
            }
        }
    }

    // Envia dados via Bluetooth
    private void sendData(String message) {
        if (btSocket != null && outputStream != null) {
            try {
                outputStream.write(message.getBytes());
                Toast.makeText(this, "Enviado: " + message, Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(this, "Erro ao enviar dados", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Não conectado", Toast.LENGTH_SHORT).show();
        }
    }

    // Thread para receber dados do ESP32
    private void startDataListening() {
        stopThread = false;
        buffer = new byte[1024];

        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !stopThread) {
                try {
                    int byteCount = inputStream.read(buffer); // Leitura dos dados do ESP32

                    if (byteCount > 0) {
                        String receivedData = new String(buffer, 0, byteCount);
                        // Processar os dados recebidos aqui
                        handler.obtainMessage(receivedData.charAt(0)).sendToTarget(); // Atualiza a UI
                    }
                } catch (IOException e) {
                    e.printStackTrace(); // Tratamento da exceção de I/O
                    stopThread = true;  // Interrompe a thread em caso de erro
                }
            }
        });

        thread.start(); // Inicia a thread
    }

    // Libera recursos quando a atividade é destruída
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopThread = true;  // Interrompe a thread ao destruir a atividade
        if (btSocket != null) {
            try {
                btSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
