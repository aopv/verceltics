import 'generated/verceltics_bridge.g.dart';

export 'generated/verceltics_bridge.g.dart'
    show
        RegistrarDomainAction,
        RegistrarDomainFlutterApi,
        RegistrarDomainSnapshot,
        RegistrarDomainStatusTone;

abstract interface class RegistrarDomainRepository {
  Future<RegistrarDomainSnapshot> load();

  Future<void> perform(RegistrarDomainAction action);

  void setChangeListener(void Function()? listener);
}

final class NativeRegistrarDomainRepository
    implements RegistrarDomainRepository, RegistrarDomainFlutterApi {
  void Function()? _changeListener;

  @override
  Future<RegistrarDomainSnapshot> load() {
    return RegistrarDomainHostApi().getRegistrarDomain();
  }

  @override
  Future<void> perform(RegistrarDomainAction action) {
    return RegistrarDomainHostApi().performRegistrarDomainAction(action);
  }

  @override
  void registrarDomainDidChange() {
    _changeListener?.call();
  }

  @override
  void setChangeListener(void Function()? listener) {
    _changeListener = listener;
    RegistrarDomainFlutterApi.setUp(listener == null ? null : this);
  }
}
