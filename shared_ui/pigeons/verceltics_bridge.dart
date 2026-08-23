import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/src/bridge/generated/verceltics_bridge.g.dart',
    dartPackageName: 'verceltics_shared_ui',
    swiftOut: '../ios/verceltics/FlutterBridge/VercelticsBridge.g.swift',
  ),
)
enum RegistrarDomainAction {
  openDomain,
  openRegistrar,
  openCompleteRegistrarApi,
}

enum RegistrarDomainStatusTone { success, warning, danger, progress, neutral }

class RegistrarDomainSnapshot {
  RegistrarDomainSnapshot({
    required this.schemaVersion,
    required this.accountId,
    required this.providerId,
    required this.providerName,
    required this.domainName,
    required this.statusLabel,
    required this.statusTone,
    required this.expiryTone,
    required this.expiryValue,
    required this.expiryLabel,
    required this.expiryDateLabel,
    required this.autoRenewLabel,
    required this.transferLockLabel,
    required this.privacyLabel,
    required this.registeredDateLabel,
    required this.nameservers,
    required this.canOpenDomain,
    required this.canOpenRegistrar,
  });

  int schemaVersion;
  String accountId;
  String providerId;
  String providerName;
  String domainName;
  String statusLabel;
  RegistrarDomainStatusTone statusTone;
  RegistrarDomainStatusTone expiryTone;
  String expiryValue;
  String expiryLabel;
  String? expiryDateLabel;
  String autoRenewLabel;
  String transferLockLabel;
  String privacyLabel;
  String? registeredDateLabel;
  List<String> nameservers;
  bool canOpenDomain;
  bool canOpenRegistrar;
}

@HostApi()
abstract class RegistrarDomainHostApi {
  RegistrarDomainSnapshot getRegistrarDomain();

  void performRegistrarDomainAction(RegistrarDomainAction action);
}

@FlutterApi()
abstract class RegistrarDomainFlutterApi {
  void registrarDomainDidChange();
}
