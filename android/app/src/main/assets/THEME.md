# Relay Theme Guide

This guide explains how to build and maintain `theme.yaml` for Relay hooks.

## Core Principles

1. **Leverage Built-in Styles**: The `themed-styler` engine provides sensible defaults for standard HTML tags. Don't redefine them unless you need to change a theme-specific value (like color).
2. **Use Variables**: Define colors, spacing, and other constants in the `variables` section. Reference them using `var(name)`.
3. **Utility Classes**: Use Tailwind-like utility classes in your JSX for layout, padding, and margins. The transpiler and styler support many standard utility patterns.
4. **Theme Inheritance**: Use `inherits` to avoid duplicating selectors between light and dark modes. Only override `variables` in sub-themes.

## Built-in Tag Defaults

The following tags have built-in styles in the engine:

| Tag | Default Styles |
|-----|----------------|
| `div` | `width: match_parent`, `flexDirection: column` |
| `p` | `width: match_parent`, `marginVertical: 16dp` |
| `h1` | `width: match_parent`, `fontSize: 32sp`, `fontWeight: bold`, `marginVertical: 21dp` |
| `h2` | `width: match_parent`, `fontSize: 24sp`, `fontWeight: bold`, `marginVertical: 20dp` |
| `h3` | `width: match_parent`, `fontSize: 18sp`, `fontWeight: bold`, `marginVertical: 18dp` |
| `button` | `padding: 8dp 16dp`, `borderRadius: 4dp`, `backgroundColor: #2196F3`, `color: #ffffff` |
| `input` | `padding: 8dp 12dp`, `borderRadius: 4dp`, `borderWidth: 1dp`, `minHeight: 40dp` |

## Proper `theme.yaml` Structure

### 1. Variables
Define your color palette here.

```yaml
variables:
  bg: "#ffffff"
  text: "#111827"
  primary: "#3b82f6"
```

### 2. Selectors
Use selectors to map variables to tags or to define custom classes.

```yaml
selectors:
  # Global tag overrides (mostly for colors)
  "body":
    backgroundColor: "var(bg)"
    width: "100%"
    height: "100%"
  "span, h1, h2, p":
    color: "var(text)"

  # Custom app classes
  ".card":
    backgroundColor: "var(surface)"
    borderRadius: 8
    padding: 16
    boxShadow: "0 4px 6px rgba(0,0,0,0.1)"
```

## Best Practices

- **Don't add `width: 100%` to `div`, `p`, or `h1-h6`**: They are already `match_parent` by default.
- **Use `body` as the root**: Wrap your hook content in a `<body>` tag to apply global background colors and ensure it fills the screen.
- **Keep it Simple**: Only add selectors to `theme.yaml` if they are shared across multiple hooks or require theme-switching logic. For one-off styles, use utility classes in JSX.
