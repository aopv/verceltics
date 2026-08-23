import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:verceltics_shared_ui/main.dart';
import 'package:verceltics_shared_ui/src/bridge/registrar_domain_repository.dart';

final class _FakeRegistrarDomainRepository
    implements RegistrarDomainRepository {
  final actions = <RegistrarDomainAction>[];
  var loadCount = 0;
  var domainName = 'example.dev';
  Completer<void>? loadGate;
  Object? loadError;
  void Function()? _changeListener;

  @override
  Future<RegistrarDomainSnapshot> load() async {
    loadCount += 1;
    await loadGate?.future;
    if (loadError case final Object error) {
      throw error;
    }
    return RegistrarDomainSnapshot(
      schemaVersion: 1,
      accountId: 'opaque-account-id',
      providerId: 'namecheap',
      providerName: 'Namecheap',
      domainName: domainName,
      statusLabel: 'ACTIVE',
      statusTone: RegistrarDomainStatusTone.success,
      expiryTone: RegistrarDomainStatusTone.success,
      expiryValue: '83',
      expiryLabel: 'days left',
      expiryDateLabel: 'Nov 15, 2026',
      autoRenewLabel: 'On',
      transferLockLabel: 'On',
      privacyLabel: 'Off',
      registeredDateLabel: 'Jan 12, 2024',
      nameservers: <String>['dns1.example.dev', 'dns2.example.dev'],
      canOpenDomain: true,
      canOpenRegistrar: true,
    );
  }

  @override
  Future<void> perform(RegistrarDomainAction action) async {
    actions.add(action);
  }

  @override
  void setChangeListener(void Function()? listener) {
    _changeListener = listener;
  }

  void emitChange() {
    _changeListener?.call();
  }
}

void main() {
  testWidgets('renders a native-backed registrar domain snapshot and actions', (
    tester,
  ) async {
    final repository = _FakeRegistrarDomainRepository();
    await tester.pumpWidget(
      VercelticsSharedApp(registrarDomainRepository: repository),
    );
    await tester.pumpAndSettle();

    expect(repository.loadCount, 1);
    expect(find.text('example.dev'), findsOneWidget);
    expect(find.text('83'), findsOneWidget);
    expect(find.text('DAYS LEFT'), findsOneWidget);
    expect(find.text('Auto renewal'), findsOneWidget);
    expect(find.text('dns1.example.dev'), findsOneWidget);

    await tester.tap(
      find.byKey(const ValueKey('registrar-domain.open-domain')),
    );
    await tester.pump();
    expect(repository.actions, <RegistrarDomainAction>[
      RegistrarDomainAction.openDomain,
    ]);

    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('registrar-domain.complete-api')),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(
      find.byKey(const ValueKey('registrar-domain.complete-api')),
    );
    await tester.pump();
    expect(
      repository.actions.last,
      RegistrarDomainAction.openCompleteRegistrarApi,
    );
  });

  testWidgets('uses the dark Verceltics palette without changing content', (
    tester,
  ) async {
    tester.platformDispatcher.platformBrightnessTestValue = Brightness.dark;
    addTearDown(tester.platformDispatcher.clearPlatformBrightnessTestValue);
    final repository = _FakeRegistrarDomainRepository();

    await tester.pumpWidget(
      VercelticsSharedApp(registrarDomainRepository: repository),
    );
    await tester.pumpAndSettle();

    expect(find.text('example.dev'), findsOneWidget);
    expect(
      Theme.of(tester.element(find.text('example.dev'))).brightness,
      Brightness.dark,
    );
  });

  testWidgets('refreshes when Swift pushes a native snapshot change', (
    tester,
  ) async {
    final repository = _FakeRegistrarDomainRepository();
    await tester.pumpWidget(
      VercelticsSharedApp(registrarDomainRepository: repository),
    );
    await tester.pumpAndSettle();

    repository.domainName = 'updated.dev';
    repository.emitChange();
    await tester.pumpAndSettle();

    expect(repository.loadCount, 2);
    expect(find.text('updated.dev'), findsOneWidget);
    expect(find.text('example.dev'), findsNothing);
  });

  testWidgets('stacks hero actions at accessibility text sizes', (
    tester,
  ) async {
    tester.platformDispatcher.textScaleFactorTestValue = 2.5;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    tester.view.physicalSize = const Size(368, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _FakeRegistrarDomainRepository();

    await tester.pumpWidget(
      VercelticsSharedApp(registrarDomainRepository: repository),
    );
    await tester.pumpAndSettle();

    final domainRect = tester.getRect(
      find.byKey(const ValueKey('registrar-domain.open-domain')),
    );
    final registrarRect = tester.getRect(
      find.byKey(const ValueKey('registrar-domain.open-registrar')),
    );
    expect(domainRect.center.dx, closeTo(registrarRect.center.dx, 0.5));
    expect(domainRect.bottom, lessThan(registrarRect.top));
  });

  testWidgets('shows loading state until the native snapshot arrives', (
    tester,
  ) async {
    final repository = _FakeRegistrarDomainRepository()
      ..loadGate = Completer<void>();

    await tester.pumpWidget(
      VercelticsSharedApp(registrarDomainRepository: repository),
    );
    await tester.pump();
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    repository.loadGate?.complete();
    await tester.pumpAndSettle();
    expect(find.text('example.dev'), findsOneWidget);
  });

  testWidgets('retries after a native bridge load error', (tester) async {
    final repository = _FakeRegistrarDomainRepository()
      ..loadError = StateError('bridge unavailable');

    await tester.pumpWidget(
      VercelticsSharedApp(registrarDomainRepository: repository),
    );
    await tester.pumpAndSettle();
    expect(find.text('Domain details could not be loaded.'), findsOneWidget);

    repository.loadError = null;
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();

    expect(repository.loadCount, 2);
    expect(find.text('example.dev'), findsOneWidget);
  });
}
