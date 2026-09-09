package marvin.com.br.tiraentulho.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.room.Room;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import marvin.com.br.tiraentulho.R;
import marvin.com.br.tiraentulho.DAO.AppDatabase;
import marvin.com.br.tiraentulho.model.PercursoModel;

public class RastreamentoService extends Service {

    private static final String CHANNEL_ID = "rastreamento_percurso";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private AppDatabase db;
    private ExecutorService executor;
    private String uuidRota;
    private Integer tenantId;
    private Location ultimaLocalizacaoSalva;

    @Override
    public void onCreate() {

        super.onCreate();

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        executor =
                Executors.newSingleThreadExecutor();

        db = Room.databaseBuilder(
                getApplicationContext(),
                AppDatabase.class,
                "app-db"
        ).build();

        criarCanalNotificacao();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        android.util.Log.e(
                "TIRA_ENTULHO",
                "RastreamentoService iniciou"
        );

        if (intent == null) {
            return START_NOT_STICKY;
        }

        uuidRota = intent.getStringExtra("uuid_rota");

        tenantId = intent.getIntExtra(
                "tenant_id",
                0
        );

        if (uuidRota == null || uuidRota.trim().isEmpty()) {

            stopSelf();

            return START_NOT_STICKY;
        }

        iniciarForeground();

        iniciarAtualizacaoGPS();

        return START_STICKY;
    }

    private void criarCanalNotificacao() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Rastreamento de percurso",
                            NotificationManager.IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            manager.createNotificationChannel(channel);
        }
    }

    private void iniciarForeground() {

        Notification notification =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setContentTitle(
                                "Percurso em andamento"
                        )
                        .setContentText(
                                "Registrando localização do veículo"
                        )
                        .setSmallIcon(
                                R.drawable.ic_localizacao
                        )
                        .setOngoing(true)
                        .build();

        startForeground(
                101,
                notification
        );
    }

    private void processarLocalizacao(Location location) {

        android.util.Log.e(
                "TIRA_ENTULHO",
                "GPS -> "
                        + location.getLatitude()
                        + " / "
                        + location.getLongitude()
                        + " precisão: "
                        + location.getAccuracy()
        );

        if (!location.hasAccuracy()) {
            return;
        }

        if (location.getAccuracy() > 30) {

            android.util.Log.e(
                    "TIRA_ENTULHO",
                    "PONTO IGNORADO - precisão ruim: "
                            + location.getAccuracy()
            );

            return;
        }

        PercursoModel ponto = new PercursoModel();

        ponto.uuid_rota = uuidRota;
        ponto.tenant_id = tenantId;

        ponto.lat = location.getLatitude();
        ponto.lon = location.getLongitude();

        ponto.precisao = location.getAccuracy();

        if (location.hasSpeed()) {
            ponto.velocidade=location.getSpeed();
        } else {
            ponto.velocidade=0f;
        }

        Date agora = new Date();

        ponto.data =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                ).format(agora);

        ponto.hora =
                new SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.getDefault()
                ).format(agora);

        ponto.sincronizado = 0;
        ponto.sit = "GRAVADO";

        executor.execute(() -> {

            db.percursoDAO().inserir(ponto);

            android.util.Log.e(
                    "TIRA_ENTULHO",
                    "PONTO SALVO NO BANCO -> "
                            + ponto.lat
                            + " / "
                            + ponto.lon
            );
        });
    }

    private void iniciarAtualizacaoGPS() {

        LocationRequest request =
                new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        10000
                )
                        .setMinUpdateIntervalMillis(5000)
                        .setMinUpdateDistanceMeters(10)
                        .build();


        locationCallback = new LocationCallback() {

            @Override
            public void onLocationResult(LocationResult locationResult) {

                if (locationResult == null) {
                    return;
                }

                Location location =
                        locationResult.getLastLocation();

                if (location != null) {

                    processarLocalizacao(location);
                }
            }
        };


        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            stopSelf();
            return;
        }


        fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                getMainLooper()
        );
    }

    @Override
    public void onDestroy() {

        android.util.Log.e(
                "TIRA_ENTULHO",
                "RastreamentoService encerrado"
        );

        if (fusedLocationClient != null && locationCallback != null) {

            fusedLocationClient.removeLocationUpdates(
                    locationCallback
            );
        }

        if (executor != null) {
            executor.shutdown();
        }

        super.onDestroy();
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}