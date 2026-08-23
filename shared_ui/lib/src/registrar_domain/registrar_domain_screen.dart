import 'dart:async';

import 'package:flutter/material.dart';

import '../bridge/registrar_domain_repository.dart';
import '../theme/verceltics_theme.dart';

class RegistrarDomainScreen extends StatefulWidget {
  const RegistrarDomainScreen({required this.repository, super.key});

  final RegistrarDomainRepository repository;

  @override
  State<RegistrarDomainScreen> createState() => _RegistrarDomainScreenState();
}

class _RegistrarDomainScreenState extends State<RegistrarDomainScreen> {
  RegistrarDomainSnapshot? _snapshot;
  Object? _error;

  @override
  void initState() {
    super.initState();
    widget.repository.setChangeListener(_handleSnapshotChange);
    _load();
  }

  @override
  void didUpdateWidget(covariant RegistrarDomainScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.repository, widget.repository)) {
      oldWidget.repository.setChangeListener(null);
      widget.repository.setChangeListener(_handleSnapshotChange);
      _load();
    }
  }

  @override
  void dispose() {
    widget.repository.setChangeListener(null);
    super.dispose();
  }

  void _handleSnapshotChange() {
    unawaited(_load(showLoading: false));
  }

  Future<void> _load({bool showLoading = true}) async {
    setState(() {
      if (showLoading) {
        _snapshot = null;
      }
      _error = null;
    });
    try {
      final snapshot = await widget.repository.load();
      if (!mounted) {
        return;
      }
      setState(() => _snapshot = snapshot);
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() => _error = error);
    }
  }

  Future<void> _perform(RegistrarDomainAction action) async {
    try {
      await widget.repository.perform(action);
    } catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not complete that action: $error')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    return Scaffold(
      backgroundColor: palette.canvas,
      body: switch ((_snapshot, _error)) {
        (final RegistrarDomainSnapshot snapshot, _) => _RegistrarDomainContent(
          snapshot: snapshot,
          onAction: _perform,
        ),
        (null, final Object error) => _LoadFailure(error: error, retry: _load),
        _ => const _LoadingState(),
      },
    );
  }
}

class _LoadingState extends StatelessWidget {
  const _LoadingState();

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    return Center(
      child: Semantics(
        label: 'Loading domain details',
        child: CircularProgressIndicator(color: palette.signal),
      ),
    );
  }
}

class _LoadFailure extends StatelessWidget {
  const _LoadFailure({required this.error, required this.retry});

  final Object error;
  final VoidCallback retry;

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: _NeoPanel(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Icon(
                  Icons.sync_problem_rounded,
                  color: palette.danger,
                  size: 32,
                ),
                const SizedBox(height: 10),
                Text(
                  'Domain details could not be loaded.',
                  textAlign: TextAlign.center,
                  style: _displayStyle(context, 18),
                ),
                const SizedBox(height: 6),
                Text(
                  error.toString(),
                  textAlign: TextAlign.center,
                  style: TextStyle(color: palette.textSecondary),
                ),
                const SizedBox(height: 14),
                _NeoActionButton(
                  label: 'Try again',
                  icon: Icons.refresh_rounded,
                  onPressed: retry,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _RegistrarDomainContent extends StatelessWidget {
  const _RegistrarDomainContent({
    required this.snapshot,
    required this.onAction,
  });

  final RegistrarDomainSnapshot snapshot;
  final ValueChanged<RegistrarDomainAction> onAction;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final horizontalPadding = constraints.maxWidth >= 700 ? 24.0 : 16.0;
        return SingleChildScrollView(
          key: const ValueKey('registrar-domain.scroll'),
          padding: EdgeInsets.fromLTRB(
            horizontalPadding,
            16,
            horizontalPadding,
            32,
          ),
          child: Align(
            alignment: Alignment.topCenter,
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 920),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  _DomainHero(snapshot: snapshot, onAction: onAction),
                  const SizedBox(height: 16),
                  if (constraints.maxWidth >= 760)
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Expanded(child: _PropertiesPanel(snapshot: snapshot)),
                        const SizedBox(width: 16),
                        Expanded(child: _NameserverPanel(snapshot: snapshot)),
                      ],
                    )
                  else ...<Widget>[
                    _PropertiesPanel(snapshot: snapshot),
                    const SizedBox(height: 16),
                    _NameserverPanel(snapshot: snapshot),
                  ],
                  const SizedBox(height: 16),
                  _ApiCatalogButton(
                    onPressed: () => onAction(
                      RegistrarDomainAction.openCompleteRegistrarApi,
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _DomainHero extends StatelessWidget {
  const _DomainHero({required this.snapshot, required this.onAction});

  final RegistrarDomainSnapshot snapshot;
  final ValueChanged<RegistrarDomainAction> onAction;

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    final brightness = Theme.of(context).brightness;
    final accent = registrarProviderAccent(snapshot.providerId, brightness);
    final statusColor = _statusColor(palette, snapshot.statusTone);
    final expiryColor = _statusColor(palette, snapshot.expiryTone);

    return _NeoPanel(
      accent: accent,
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: <Color>[
          accent.withValues(alpha: brightness == Brightness.dark ? 0.24 : 0.17),
          palette.surface,
          palette.surface,
        ],
        stops: const <double>[0, 0.48, 1],
      ),
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                _ProviderMark(
                  providerName: snapshot.providerName,
                  accent: accent,
                ),
                const SizedBox(width: 13),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        snapshot.domainName,
                        key: const ValueKey('registrar-domain.name'),
                        style: _displayStyle(context, 22),
                      ),
                      const SizedBox(height: 7),
                      _StatusBadge(
                        label: snapshot.statusLabel,
                        color: statusColor,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 19),
            _ExpirySummary(snapshot: snapshot, color: expiryColor),
            if (snapshot.canOpenDomain ||
                snapshot.canOpenRegistrar) ...<Widget>[
              const SizedBox(height: 17),
              _HeroActions(snapshot: snapshot, onAction: onAction),
            ],
          ],
        ),
      ),
    );
  }
}

class _ExpirySummary extends StatelessWidget {
  const _ExpirySummary({required this.snapshot, required this.color});

  final RegistrarDomainSnapshot snapshot;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    final stacksMetrics = MediaQuery.textScalerOf(context).scale(16) > 20;
    final value = Text(
      snapshot.expiryValue,
      style: _displayStyle(context, 38, color: color, tabularFigures: true),
    );
    final label = Padding(
      padding: const EdgeInsets.only(bottom: 5),
      child: Text(
        snapshot.expiryLabel.toUpperCase(),
        style: _labelStyle(context),
      ),
    );
    final dateLabel = snapshot.expiryDateLabel;
    final date = dateLabel == null
        ? null
        : Text(
            dateLabel,
            style: TextStyle(
              color: palette.textSecondary,
              fontWeight: FontWeight.w700,
            ),
          );

    if (stacksMetrics) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Wrap(
            crossAxisAlignment: WrapCrossAlignment.end,
            spacing: 8,
            runSpacing: 5,
            children: <Widget>[value, label],
          ),
          if (date != null) ...<Widget>[const SizedBox(height: 7), date],
        ],
      );
    }

    return Wrap(
      alignment: WrapAlignment.spaceBetween,
      crossAxisAlignment: WrapCrossAlignment.end,
      spacing: 16,
      runSpacing: 8,
      children: <Widget>[
        Row(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: <Widget>[value, const SizedBox(width: 8), label],
        ),
        ?date,
      ],
    );
  }
}

class _HeroActions extends StatelessWidget {
  const _HeroActions({required this.snapshot, required this.onAction});

  final RegistrarDomainSnapshot snapshot;
  final ValueChanged<RegistrarDomainAction> onAction;

  @override
  Widget build(BuildContext context) {
    final stacksActions = MediaQuery.textScalerOf(context).scale(16) > 20;
    final actions = <Widget>[
      if (snapshot.canOpenDomain)
        _NeoActionButton(
          key: const ValueKey('registrar-domain.open-domain'),
          label: 'Open domain',
          icon: Icons.open_in_new_rounded,
          onPressed: () => onAction(RegistrarDomainAction.openDomain),
        ),
      if (snapshot.canOpenRegistrar)
        _NeoActionButton(
          key: const ValueKey('registrar-domain.open-registrar'),
          label: 'Registrar',
          icon: Icons.public_rounded,
          onPressed: () => onAction(RegistrarDomainAction.openRegistrar),
        ),
    ];

    if (stacksActions) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: actions
            .expand((action) sync* {
              if (action != actions.first) {
                yield const SizedBox(height: 10);
              }
              yield action;
            })
            .toList(growable: false),
      );
    }

    return Row(
      children: actions
          .expand((action) sync* {
            if (action != actions.first) {
              yield const SizedBox(width: 10);
            }
            yield Expanded(child: action);
          })
          .toList(growable: false),
    );
  }
}

class _PropertiesPanel extends StatelessWidget {
  const _PropertiesPanel({required this.snapshot});

  final RegistrarDomainSnapshot snapshot;

  @override
  Widget build(BuildContext context) {
    return _NeoPanel(
      child: Column(
        children: <Widget>[
          _PropertyRow(
            icon: Icons.autorenew_rounded,
            title: 'Auto renewal',
            value: snapshot.autoRenewLabel,
          ),
          const _InkDivider(),
          _PropertyRow(
            icon: Icons.lock_rounded,
            title: 'Transfer lock',
            value: snapshot.transferLockLabel,
          ),
          const _InkDivider(),
          _PropertyRow(
            icon: Icons.visibility_off_rounded,
            title: 'WHOIS privacy',
            value: snapshot.privacyLabel,
          ),
          if (snapshot.registeredDateLabel case final String label) ...<Widget>[
            const _InkDivider(),
            _PropertyRow(
              icon: Icons.event_available_rounded,
              title: 'Registered',
              value: label,
            ),
          ],
        ],
      ),
    );
  }
}

class _PropertyRow extends StatelessWidget {
  const _PropertyRow({
    required this.icon,
    required this.title,
    required this.value,
  });

  final IconData icon;
  final String title;
  final String value;

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 13),
      child: Row(
        children: <Widget>[
          Icon(icon, size: 18, color: palette.signal),
          const SizedBox(width: 12),
          Expanded(
            child: Text(title, style: TextStyle(color: palette.textSecondary)),
          ),
          const SizedBox(width: 12),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.end,
              style: const TextStyle(fontWeight: FontWeight.w800),
            ),
          ),
        ],
      ),
    );
  }
}

class _InkDivider extends StatelessWidget {
  const _InkDivider();

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    return Divider(
      height: 1,
      thickness: 1,
      indent: 48,
      color: palette.stroke.withValues(alpha: 0.28),
    );
  }
}

class _NameserverPanel extends StatelessWidget {
  const _NameserverPanel({required this.snapshot});

  final RegistrarDomainSnapshot snapshot;

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    return _NeoPanel(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              children: <Widget>[
                Icon(Icons.dns_rounded, color: palette.signal, size: 19),
                const SizedBox(width: 9),
                Flexible(
                  child: Text(
                    'NAMESERVERS',
                    maxLines: 2,
                    style: _labelStyle(context),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            if (snapshot.nameservers.isEmpty)
              Text(
                'The list endpoint did not include nameservers. Open the API explorer for the domain detail or DNS route.',
                style: TextStyle(color: palette.textSecondary, height: 1.35),
              )
            else
              for (final nameserver in snapshot.nameservers)
                Padding(
                  padding: const EdgeInsets.only(bottom: 7),
                  child: SelectableText(
                    nameserver,
                    style: TextStyle(
                      color: palette.textSecondary,
                      fontFamily: 'monospace',
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
          ],
        ),
      ),
    );
  }
}

class _ApiCatalogButton extends StatelessWidget {
  const _ApiCatalogButton({required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    return _NeoPanel(
      child: InkWell(
        key: const ValueKey('registrar-domain.complete-api'),
        onTap: onPressed,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: <Widget>[
              _SquareIcon(icon: Icons.terminal_rounded, color: palette.signal),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      'Complete registrar API',
                      style: const TextStyle(fontWeight: FontWeight.w800),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      'Search every indexed read and write operation, then inspect the full raw response',
                      style: TextStyle(
                        color: palette.textSecondary,
                        fontSize: 12,
                        height: 1.3,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              Icon(Icons.chevron_right_rounded, color: palette.textTertiary),
            ],
          ),
        ),
      ),
    );
  }
}

class _ProviderMark extends StatelessWidget {
  const _ProviderMark({required this.providerName, required this.accent});

  final String providerName;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    return Semantics(
      label: '$providerName registrar',
      child: Container(
        width: 54,
        height: 54,
        decoration: BoxDecoration(
          color: palette.textPrimary,
          borderRadius: BorderRadius.circular(3),
          border: Border.all(color: palette.stroke, width: 1.5),
        ),
        child: Icon(Icons.language_rounded, color: accent, size: 30),
      ),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    return Align(
      alignment: Alignment.centerLeft,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 6),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.16),
          borderRadius: BorderRadius.circular(3),
          border: Border.all(color: color, width: 1.4),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Container(
              width: 6,
              height: 6,
              decoration: BoxDecoration(color: color, shape: BoxShape.circle),
            ),
            const SizedBox(width: 6),
            Flexible(
              child: Text(
                label.toUpperCase(),
                overflow: TextOverflow.ellipsis,
                style: _labelStyle(context)
                    .copyWith(color: palette.textPrimary, fontSize: 11),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SquareIcon extends StatelessWidget {
  const _SquareIcon({required this.icon, required this.color});

  final IconData icon;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    return Container(
      width: 38,
      height: 38,
      decoration: BoxDecoration(
        color: palette.surfaceRaised,
        borderRadius: BorderRadius.circular(3),
        border: Border.all(color: palette.stroke, width: 1.3),
      ),
      child: Icon(icon, color: color, size: 20),
    );
  }
}

class _NeoActionButton extends StatelessWidget {
  const _NeoActionButton({
    required this.label,
    required this.icon,
    required this.onPressed,
    super.key,
  });

  final String label;
  final IconData icon;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    return Material(
      color: palette.surfaceRaised,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(3),
        side: BorderSide(color: palette.stroke, width: 1.5),
      ),
      child: InkWell(
        onTap: onPressed,
        borderRadius: BorderRadius.circular(3),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 46),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                Icon(icon, size: 17),
                const SizedBox(width: 7),
                Flexible(
                  child: Text(
                    label,
                    maxLines: 2,
                    textAlign: TextAlign.center,
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _NeoPanel extends StatelessWidget {
  const _NeoPanel({required this.child, this.accent, this.gradient});

  final Widget child;
  final Color? accent;
  final Gradient? gradient;

  @override
  Widget build(BuildContext context) {
    final palette = VercelticsPalette.of(context);
    return Container(
      decoration: BoxDecoration(
        color: gradient == null ? palette.surface : null,
        gradient: gradient,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: palette.stroke, width: 1.8),
        boxShadow: <BoxShadow>[
          BoxShadow(
            color: palette.shadow.withValues(alpha: 0.92),
            offset: const Offset(4, 4),
          ),
        ],
      ),
      child: Stack(
        children: <Widget>[
          if (accent case final Color value)
            Positioned(
              left: 0,
              top: 0,
              bottom: 0,
              width: 4,
              child: ColoredBox(color: value),
            ),
          child,
        ],
      ),
    );
  }
}

TextStyle _displayStyle(
  BuildContext context,
  double size, {
  Color? color,
  bool tabularFigures = false,
}) {
  return TextStyle(
    color: color ?? VercelticsPalette.of(context).textPrimary,
    fontSize: size,
    fontWeight: FontWeight.w900,
    height: 1.02,
    letterSpacing: -0.65,
    fontFeatures: tabularFigures
        ? const <FontFeature>[FontFeature.tabularFigures()]
        : null,
  );
}

TextStyle _labelStyle(BuildContext context) {
  return TextStyle(
    color: VercelticsPalette.of(context).textSecondary,
    fontSize: 12,
    fontWeight: FontWeight.w900,
    letterSpacing: 0.55,
  );
}

Color _statusColor(VercelticsPalette palette, RegistrarDomainStatusTone tone) {
  return switch (tone) {
    RegistrarDomainStatusTone.success => palette.success,
    RegistrarDomainStatusTone.warning => palette.warning,
    RegistrarDomainStatusTone.danger => palette.danger,
    RegistrarDomainStatusTone.progress => palette.signal,
    RegistrarDomainStatusTone.neutral => palette.textSecondary,
  };
}
