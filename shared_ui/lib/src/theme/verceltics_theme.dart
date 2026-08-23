import 'package:flutter/material.dart';

@immutable
class VercelticsPalette extends ThemeExtension<VercelticsPalette> {
  const VercelticsPalette({
    required this.canvas,
    required this.surface,
    required this.surfaceRaised,
    required this.textPrimary,
    required this.textSecondary,
    required this.textTertiary,
    required this.stroke,
    required this.shadow,
    required this.signal,
    required this.success,
    required this.warning,
    required this.danger,
  });

  static const light = VercelticsPalette(
    canvas: Color(0xFFF8F3E8),
    surface: Color(0xFFFFFDF6),
    surfaceRaised: Color(0xFFECE3D2),
    textPrimary: Color(0xFF0E0D0A),
    textSecondary: Color(0xFF5C5346),
    textTertiary: Color(0xFF6A5F50),
    stroke: Color(0xFF000000),
    shadow: Color(0xFF0A0907),
    signal: Color(0xFFFF6109),
    success: Color(0xFF147331),
    warning: Color(0xFF855000),
    danger: Color(0xFFC21A24),
  );

  static const dark = VercelticsPalette(
    canvas: Color(0xFF0C0B09),
    surface: Color(0xFF1B1713),
    surfaceRaised: Color(0xFF28231C),
    textPrimary: Color(0xFFF9F2E4),
    textSecondary: Color(0xFFBAAF9D),
    textTertiary: Color(0xFF8A7F6C),
    stroke: Color(0xFFECE1CF),
    shadow: Color(0xFF000000),
    signal: Color(0xFFFF8530),
    success: Color(0xFF61DB70),
    warning: Color(0xFFFFB833),
    danger: Color(0xFFFF5C63),
  );

  final Color canvas;
  final Color surface;
  final Color surfaceRaised;
  final Color textPrimary;
  final Color textSecondary;
  final Color textTertiary;
  final Color stroke;
  final Color shadow;
  final Color signal;
  final Color success;
  final Color warning;
  final Color danger;

  static VercelticsPalette of(BuildContext context) {
    return Theme.of(context).extension<VercelticsPalette>()!;
  }

  @override
  VercelticsPalette copyWith({
    Color? canvas,
    Color? surface,
    Color? surfaceRaised,
    Color? textPrimary,
    Color? textSecondary,
    Color? textTertiary,
    Color? stroke,
    Color? shadow,
    Color? signal,
    Color? success,
    Color? warning,
    Color? danger,
  }) {
    return VercelticsPalette(
      canvas: canvas ?? this.canvas,
      surface: surface ?? this.surface,
      surfaceRaised: surfaceRaised ?? this.surfaceRaised,
      textPrimary: textPrimary ?? this.textPrimary,
      textSecondary: textSecondary ?? this.textSecondary,
      textTertiary: textTertiary ?? this.textTertiary,
      stroke: stroke ?? this.stroke,
      shadow: shadow ?? this.shadow,
      signal: signal ?? this.signal,
      success: success ?? this.success,
      warning: warning ?? this.warning,
      danger: danger ?? this.danger,
    );
  }

  @override
  VercelticsPalette lerp(
    covariant ThemeExtension<VercelticsPalette>? other,
    double t,
  ) {
    if (other is! VercelticsPalette) {
      return this;
    }
    return VercelticsPalette(
      canvas: Color.lerp(canvas, other.canvas, t)!,
      surface: Color.lerp(surface, other.surface, t)!,
      surfaceRaised: Color.lerp(surfaceRaised, other.surfaceRaised, t)!,
      textPrimary: Color.lerp(textPrimary, other.textPrimary, t)!,
      textSecondary: Color.lerp(textSecondary, other.textSecondary, t)!,
      textTertiary: Color.lerp(textTertiary, other.textTertiary, t)!,
      stroke: Color.lerp(stroke, other.stroke, t)!,
      shadow: Color.lerp(shadow, other.shadow, t)!,
      signal: Color.lerp(signal, other.signal, t)!,
      success: Color.lerp(success, other.success, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      danger: Color.lerp(danger, other.danger, t)!,
    );
  }
}

abstract final class VercelticsTheme {
  static ThemeData get light => _theme(VercelticsPalette.light);

  static ThemeData get dark => _theme(VercelticsPalette.dark);

  static ThemeData _theme(VercelticsPalette palette) {
    final brightness = identical(palette, VercelticsPalette.dark)
        ? Brightness.dark
        : Brightness.light;
    final colorScheme = ColorScheme.fromSeed(
      seedColor: palette.signal,
      brightness: brightness,
      surface: palette.surface,
    );

    return ThemeData(
      brightness: brightness,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: palette.canvas,
      splashFactory: InkSparkle.splashFactory,
      materialTapTargetSize: MaterialTapTargetSize.padded,
      visualDensity: VisualDensity.standard,
      textTheme: ThemeData(brightness: brightness).textTheme.apply(
        bodyColor: palette.textPrimary,
        displayColor: palette.textPrimary,
      ),
      extensions: <ThemeExtension<dynamic>>[palette],
    );
  }
}

Color registrarProviderAccent(String providerId, Brightness brightness) {
  final dark = brightness == Brightness.dark;
  return switch (providerId) {
    'nameDotCom' => dark ? const Color(0xFF65ABFF) : const Color(0xFF298CF5),
    'namecheap' => dark ? const Color(0xFFFF7C4A) : const Color(0xFFFF5E1F),
    'porkbun' => dark ? const Color(0xFFFF7F99) : const Color(0xFFF25E87),
    'spaceship' => dark ? const Color(0xFFA596FF) : const Color(0xFF806BFA),
    'dynadot' => dark ? const Color(0xFF70D2F5) : const Color(0xFF33B3EB),
    'nameSilo' => dark ? const Color(0xFF63DDAF) : const Color(0xFF24B88C),
    'gandi' => dark ? const Color(0xFF9F93FF) : const Color(0xFF6B61EB),
    'goDaddy' => dark ? const Color(0xFF5DDECB) : const Color(0xFF1FB89F),
    _ => dark ? const Color(0xFFFF8530) : const Color(0xFFFF6109),
  };
}
