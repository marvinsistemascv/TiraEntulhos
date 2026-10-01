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
import android.util.Log;

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

import marvin.com.br.tiraentulho.DAO.AppDatabase;
import marvin.com.br.tiraentulho.R;
import marvin.com.br.tiraentulho.model.PercursoModel;

public class RastreamentoService extends Service {

    private static final String CHANNEL_ID =
            "rastreamento_percurso";

    private static final String TAG =
            "TIRA_ENTULHO";


    /*
     * ============================================================
     * CONFIGURAÇÕES DO GPS
     * ============================================================
     */

    // Precisão máxima aceita em metros.
    private static final float PRECISAO_MAXIMA_METROS = 30f;

    // Evita gravar vários pontos praticamente no mesmo lugar.
    private static final float DISTANCIA_MINIMA_METROS = 10f;

    /*
     * Velocidade máxima considerada possível.
     *
     * 100 km/h é mais do que suficiente para um caminhão
     * de coleta urbana e ainda deixa boa margem para erro do GPS.
     */
    private static final float VELOCIDADE_MAXIMA_KMH = 100f;


    /*
     * Intervalo normal desejado.
     *
     * 10 segundos continua sendo bom para desenhar
     * trajetos urbanos.
     */
    private static final long INTERVALO_GPS_MS = 10_000;

    /*
     * Não precisamos aceitar atualização a cada 5 segundos.
     * Isso reduz um pouco o trabalho do GPS.
     */
    private static final long INTERVALO_MINIMO_GPS_MS = 10_000;


    private FusedLocationProviderClient fusedLocationClient;

    private LocationCallback locationCallback;

    private AppDatabase db;

    private ExecutorService executor;

    private String uuidRota;

    private Integer tenantId;

    /*
     * Último ponto que passou por TODOS os filtros
     * e foi considerado válido.
     */
    private Location ultimaLocalizacaoSalva;

    /*
     * Evita registrar duas vezes o callback caso
     * onStartCommand seja chamado novamente.
     */
    private boolean rastreamentoAtivo = false;


    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    public void onCreate() {

        super.onCreate();

        Log.i(
                TAG,
                "RastreamentoService criado"
        );


        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(
                        this
                );


        executor =
                Executors.newSingleThreadExecutor();


        db =
                Room.databaseBuilder(
                        getApplicationContext(),
                        AppDatabase.class,
                        "app-db"
                ).build();


        criarCanalNotificacao();
    }


    // ============================================================
    // START SERVICE
    // ============================================================

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        Log.i(
                TAG,
                "RastreamentoService iniciou"
        );


        if (intent == null) {

            Log.e(
                    TAG,
                    "Intent do RastreamentoService está NULL"
            );

            stopSelf();

            return START_NOT_STICKY;
        }


        uuidRota =
                intent.getStringExtra(
                        "uuid_rota"
                );


        tenantId =
                intent.getIntExtra(
                        "tenant_id",
                        0
                );


        if (uuidRota == null
                || uuidRota.trim().isEmpty()) {

            Log.e(
                    TAG,
                    "UUID da rota inválido. Serviço encerrado."
            );

            stopSelf();

            return START_NOT_STICKY;
        }


        /*
         * Foreground precisa começar imediatamente.
         */
        iniciarForeground();


        /*
         * Só inicia novamente se ainda não estiver
         * recebendo localização.
         */
        if (!rastreamentoAtivo) {

            iniciarAtualizacaoGPS();
        }


        /*
         * IMPORTANTE:
         *
         * Não queremos que o Android recrie sozinho este serviço
         * depois que ele for encerrado.
         *
         * Uma nova rota inicia explicitamente o serviço novamente.
         */
        return START_NOT_STICKY;
    }


    // ============================================================
    // NOTIFICAÇÃO
    // ============================================================

    private void criarCanalNotificacao() {

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.O) {

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


            if (manager != null) {

                manager.createNotificationChannel(
                        channel
                );
            }
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
                        .setOnlyAlertOnce(true)
                        .build();


        startForeground(
                101,
                notification
        );
    }


    // ============================================================
    // INICIA GPS
    // ============================================================

    private void iniciarAtualizacaoGPS() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            Log.e(
                    TAG,
                    "Sem permissão ACCESS_FINE_LOCATION"
            );

            stopSelf();

            return;
        }


        LocationRequest request =
                new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        INTERVALO_GPS_MS
                )
                        .setMinUpdateIntervalMillis(
                                INTERVALO_MINIMO_GPS_MS
                        )
                        .setMinUpdateDistanceMeters(
                                DISTANCIA_MINIMA_METROS
                        )
                        .build();


        locationCallback =
                new LocationCallback() {

                    @Override
                    public void onLocationResult(
                            LocationResult locationResult
                    ) {

                        if (locationResult == null) {
                            return;
                        }


                        /*
                         * Podemos receber mais de uma localização
                         * dentro do mesmo resultado.
                         *
                         * Processamos todas na ordem.
                         */
                        for (Location location :
                                locationResult.getLocations()) {

                            if (location != null) {

                                processarLocalizacao(
                                        location
                                );
                            }
                        }
                    }
                };


        fusedLocationClient
                .requestLocationUpdates(
                        request,
                        locationCallback,
                        getMainLooper()
                )
                .addOnSuccessListener(
                        unused -> {

                            rastreamentoAtivo = true;

                            Log.i(
                                    TAG,
                                    "Atualizações GPS iniciadas"
                            );
                        }
                )
                .addOnFailureListener(
                        e -> {

                            rastreamentoAtivo = false;

                            Log.e(
                                    TAG,
                                    "Erro ao iniciar GPS",
                                    e
                            );

                            stopSelf();
                        }
                );
    }


    // ============================================================
    // PROCESSA LOCALIZAÇÃO
    // ============================================================

    private void processarLocalizacao(
            Location location
    ) {

        if (location == null) {
            return;
        }


        Log.d(
                TAG,
                "GPS RECEBIDO -> "
                        + location.getLatitude()
                        + " / "
                        + location.getLongitude()
                        + " | precisão: "
                        + location.getAccuracy()
        );


        // ========================================================
        // FILTRO 1
        // LOCALIZAÇÃO PRECISA TER INFORMAÇÃO DE PRECISÃO
        // ========================================================

        if (!location.hasAccuracy()) {

            Log.w(
                    TAG,
                    "PONTO IGNORADO -> sem informação de precisão"
            );

            return;
        }


        // ========================================================
        // FILTRO 2
        // PRECISÃO RUIM
        // ========================================================

        if (location.getAccuracy()
                > PRECISAO_MAXIMA_METROS) {

            Log.w(
                    TAG,
                    "PONTO IGNORADO -> precisão ruim: "
                            + location.getAccuracy()
                            + " metros"
            );

            return;
        }


        // ========================================================
        // FILTROS COMPARANDO COM O ÚLTIMO PONTO VÁLIDO
        // ========================================================

        if (ultimaLocalizacaoSalva != null) {

            float distancia =
                    ultimaLocalizacaoSalva.distanceTo(
                            location
                    );


            long tempoMs =
                    location.getTime()
                            - ultimaLocalizacaoSalva.getTime();


            double segundos =
                    tempoMs / 1000.0;


            Log.d(
                    TAG,
                    "DISTÂNCIA DO ÚLTIMO PONTO -> "
                            + distancia
                            + " metros em "
                            + segundos
                            + " segundos"
            );


            // ====================================================
            // FILTRO 3
            // TEMPO INVÁLIDO
            // ====================================================

            if (tempoMs <= 0) {

                Log.w(
                        TAG,
                        "PONTO IGNORADO -> timestamp inválido"
                );

                return;
            }


            // ====================================================
            // FILTRO 4
            // PONTO PRATICAMENTE PARADO
            // ====================================================

            if (distancia
                    < DISTANCIA_MINIMA_METROS) {

                Log.d(
                        TAG,
                        "PONTO IGNORADO -> deslocamento pequeno: "
                                + distancia
                                + " metros"
                );

                return;
            }


            // ====================================================
            // FILTRO 5
            // VELOCIDADE CALCULADA ENTRE OS PONTOS
            // ====================================================

            double velocidadeMs =
                    distancia / segundos;


            double velocidadeKmH =
                    velocidadeMs * 3.6;


            Log.d(
                    TAG,
                    "VELOCIDADE CALCULADA -> "
                            + velocidadeKmH
                            + " km/h"
            );


            /*
             * Aqui eliminamos os "riscos".
             *
             * Se o GPS jogar o caminhão centenas de metros
             * para longe em poucos segundos, a velocidade
             * calculada ficará absurda.
             */
            if (velocidadeKmH
                    > VELOCIDADE_MAXIMA_KMH) {

                Log.w(
                        TAG,
                        "PONTO DESCARTADO -> salto impossível: "
                                + velocidadeKmH
                                + " km/h"
                                + " | distância: "
                                + distancia
                                + " m"
                                + " | precisão: "
                                + location.getAccuracy()
                );

                return;
            }
        }


        // ========================================================
        // PASSOU POR TODOS OS FILTROS
        // ========================================================

        salvarLocalizacao(
                location
        );
    }


    // ============================================================
    // SALVA PONTO NO ROOM
    // ============================================================

    private void salvarLocalizacao(
            Location location
    ) {

        PercursoModel ponto =
                new PercursoModel();


        ponto.uuid_rota =
                uuidRota;


        ponto.tenant_id =
                tenantId;


        ponto.lat =
                location.getLatitude();


        ponto.lon =
                location.getLongitude();


        ponto.precisao =
                location.getAccuracy();


        if (location.hasSpeed()) {

            ponto.velocidade =
                    location.getSpeed();

        } else {

            ponto.velocidade =
                    0f;
        }


        Date agora =
                new Date();


        ponto.data =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                ).format(
                        agora
                );


        ponto.hora =
                new SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.getDefault()
                ).format(
                        agora
                );


        ponto.sincronizado =
                0;


        ponto.sit =
                "GRAVADO";


        /*
         * Criamos uma cópia da localização.
         *
         * Não mantemos referência ao objeto entregue
         * pelo FusedLocationProvider.
         */
        Location localizacaoAceita =
                new Location(
                        location
                );


        executor.execute(() -> {

            try {

                db.percursoDAO()
                        .inserir(
                                ponto
                        );


                /*
                 * IMPORTANTE:
                 *
                 * Só atualizamos a última localização
                 * depois que o ponto foi realmente
                 * salvo no banco.
                 */
                ultimaLocalizacaoSalva =
                        localizacaoAceita;


                Log.i(
                        TAG,
                        "PONTO SALVO -> "
                                + ponto.lat
                                + " / "
                                + ponto.lon
                                + " | precisão: "
                                + ponto.precisao
                );


            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Erro ao salvar ponto GPS",
                        e
                );
            }
        });
    }


    // ============================================================
    // PARA GPS
    // ============================================================

    private void pararAtualizacaoGPS() {

        if (fusedLocationClient == null
                || locationCallback == null) {

            rastreamentoAtivo = false;

            return;
        }


        fusedLocationClient
                .removeLocationUpdates(
                        locationCallback
                )
                .addOnCompleteListener(
                        task -> {

                            rastreamentoAtivo = false;

                            Log.i(
                                    TAG,
                                    "Atualizações GPS removidas"
                            );
                        }
                );


        locationCallback = null;
    }


    // ============================================================
    // DESTROY
    // ============================================================

    @Override
    public void onDestroy() {

        Log.i(
                TAG,
                "RastreamentoService encerrando..."
        );


        /*
         * PRIMEIRO:
         * para completamente as atualizações GPS.
         */
        pararAtualizacaoGPS();


        /*
         * Remove também o serviço de foreground
         * e a notificação.
         */
        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.N) {

            stopForeground(
                    STOP_FOREGROUND_REMOVE
            );

        } else {

            stopForeground(
                    true
            );
        }


        /*
         * Finaliza executor.
         */
        if (executor != null
                && !executor.isShutdown()) {

            executor.shutdown();
        }


        /*
         * Fecha Room criado exclusivamente
         * por este Service.
         */
        if (db != null
                && db.isOpen()) {

            db.close();
        }


        ultimaLocalizacaoSalva =
                null;


        rastreamentoAtivo =
                false;


        super.onDestroy();


        Log.i(
                TAG,
                "RastreamentoService ENCERRADO"
        );
    }


    // ============================================================
    // BIND
    // ============================================================

    @Nullable
    @Override
    public IBinder onBind(
            Intent intent
    ) {

        return null;
    }
}