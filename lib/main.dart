import 'package:flutter/material.dart';
import 'dart:async';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:readsms/readsms.dart';

final FlutterBackgroundService service = FlutterBackgroundService();

Future<void> initializeService() async {
  await service.configure(
    androidConfiguration: AndroidConfiguration(
      onStart: onStart,
      autoStart: true,
      isForegroundMode: true,
      initialNotificationTitle: "ReadSMS",
      initialNotificationContent: "대기중",
    ),
    iosConfiguration: IosConfiguration(),
  );

  service.startService();
}

@pragma('vm:entry-point')
void onStart(ServiceInstance service) async {
  debugPrint('Background service is running');

  final serviceSMS = Readsms();
  serviceSMS.read();
  serviceSMS.smsStream.listen((e) {
    debugPrint(e.body);
    debugPrint(e.sender);
    debugPrint(e.timeReceived.toString());

    service.invoke(
      'update',
      {
        'message': e.body,
        'sender': e.sender,
        'dttm': e.timeReceived.toString(),
      },
    );
  });
}

class AppMain extends StatefulWidget {
  const AppMain({super.key});

  @override
  State<AppMain> createState() => _AppMainState();
}

class _AppMainState extends State<AppMain> {
  String message = "";
  String sender = "";
  String dttm = "";

  @override
  void initState() {
    super.initState();

    service.on('update').listen((event) {
      if (event != null) {
        setState(() {
          message = event['message'] ?? "";
          sender = event['sender'] ?? "";
          dttm = event['dttm'] ?? "";
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('Background Service Example')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text('new sms received: $message\n'),
              Text('new sms Sender: $sender\n'),
              Text('new sms time: $dttm\n'),
              const Text('App is running'),
            ],
          ),
        ),
      ),
    );
  }
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  Permission.sms.request().then((status) {
    if (status.isGranted) {
      initializeService();
    }
  });

  runApp(const AppMain());
}
