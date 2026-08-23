import 'package:flutter/material.dart';

import 'src/bridge/registrar_domain_repository.dart';
import 'src/registrar_domain/registrar_domain_screen.dart';
import 'src/theme/verceltics_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const VercelticsSharedApp());
}

class VercelticsSharedApp extends StatelessWidget {
  const VercelticsSharedApp({super.key, this.registrarDomainRepository});

  final RegistrarDomainRepository? registrarDomainRepository;

  @override
  Widget build(BuildContext context) {
    final repository =
        registrarDomainRepository ?? NativeRegistrarDomainRepository();

    Widget registrarDomainBuilder(BuildContext context) {
      return RegistrarDomainScreen(repository: repository);
    }

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Verceltics',
      theme: VercelticsTheme.light,
      darkTheme: VercelticsTheme.dark,
      themeMode: ThemeMode.system,
      routes: <String, WidgetBuilder>{
        '/': registrarDomainBuilder,
        '/registrar-domain': registrarDomainBuilder,
      },
    );
  }
}
