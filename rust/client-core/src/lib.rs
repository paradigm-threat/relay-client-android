use relay_hook_transpiler::{transpile_jsx_simple, version as transpiler_version};
use themed_styler::{version, State, ThemeEntry};
use indexmap::IndexMap;
use serde::Deserialize;

#[derive(Deserialize, Default)]
struct UsageSnapshot {
    #[serde(default)]
    selectors: Vec<String>,
    #[serde(default)]
    classes: Vec<String>,
}

#[derive(Deserialize, Default)]
struct ThemesInput {
    #[serde(default)]
    themes: IndexMap<String, ThemeEntry>,
    #[serde(rename = "currentTheme", default)]
    current_theme: Option<String>,
}

fn build_state(usage: UsageSnapshot, themes_input: ThemesInput) -> State {
    let mut state = State::new_default();
    if !themes_input.themes.is_empty() {
        state.themes = themes_input.themes;
        if let Some(current) = themes_input.current_theme.clone() {
            if state.themes.contains_key(&current) {
                state.current_theme = current;
            }
        }
        if state.default_theme.is_empty() {
            if let Some((name, _)) = state.themes.iter().next() {
                state.default_theme = name.clone();
            }
        }
    }
    state.register_selectors(usage.selectors);
    for class in usage.classes {
        state.used_classes.insert(class);
    }
    state
}

pub fn render_css(usage_json: &str, themes_json: &str) -> String {
    let usage: UsageSnapshot = serde_json::from_str(usage_json).unwrap_or_default();
    let themes_input: ThemesInput = serde_json::from_str(themes_json).unwrap_or_default();
    let state = build_state(usage, themes_input);
    state.css_for_web()
}

pub fn get_rn_styles(selector: &str, classes_json: &str, themes_json: &str) -> String {
    let classes: Vec<String> = serde_json::from_str(classes_json).unwrap_or_default();
    let themes_input: ThemesInput = serde_json::from_str(themes_json).unwrap_or_default();
    let state = build_state(UsageSnapshot::default(), themes_input);
    let styles = state.rn_styles_for(selector, &classes);
    serde_json::to_string(&styles).unwrap_or_else(|_| "{}".to_string())
}

// Android-specific accessor: transform RN styles to Android-compatible layout
pub fn get_android_styles(selector: &str, classes_json: &str, themes_json: &str) -> String {
    let classes: Vec<String> = serde_json::from_str(classes_json).unwrap_or_default();
    let themes_input: ThemesInput = serde_json::from_str(themes_json).unwrap_or_default();
    let state = build_state(UsageSnapshot::default(), themes_input);
    let mut styles = state.rn_styles_for(selector, &classes);
    
    // Add Android-specific layout enhancements
    // For div/view elements, ensure proper layout behavior
    if selector == "div" || selector == "view" {
        // Ensure row containers expand horizontally
        if styles.get("flexDirection").map_or(false, |v| v.as_str() == Some("row")) {
            styles.insert("width".to_string(), serde_json::json!("match_parent"));
        }
        // Ensure column containers (default) have proper dimensions
        else if !styles.contains_key("width") {
            styles.insert("width".to_string(), serde_json::json!("match_parent"));
        }
        // Root containers should fill available space
        if selector == "div" && !styles.contains_key("height") {
            styles.insert("height".to_string(), serde_json::json!("wrap_content"));
        }
    }
    
    // For text/span, ensure proper sizing
    if selector == "span" || selector == "text" {
        if !styles.contains_key("width") {
            styles.insert("width".to_string(), serde_json::json!("wrap_content"));
        }
        if !styles.contains_key("height") {
            styles.insert("height".to_string(), serde_json::json!("wrap_content"));
        }
    }
    
    serde_json::to_string(&styles).unwrap_or_else(|_| "{}".to_string())
}


pub fn get_default_state() -> String {
    let state = State::default_state();
    serde_json::to_string(&state.to_json()).unwrap_or_else(|_| "{}".to_string())
}

use std::net::{SocketAddr, TcpStream};
use std::time::Duration;

pub fn check_port(host: &str, port: u16) -> bool {
    let addr = format!("{}:{}", host, port);
    if let Ok(socket_addr) = addr.parse::<SocketAddr>() {
        TcpStream::connect_timeout(&socket_addr, Duration::from_millis(500)).is_ok()
    } else {
        false
    }
}

pub fn transpile_jsx(source: &str, _filename: &str, _include_runtime: bool) -> Result<String, anyhow::Error> {
    // Use simple custom JSX parser (no SWC, no TypeScript)
    let raw = transpile_jsx_simple(source).map_err(|e| anyhow::anyhow!(e))?;

    // Handle `export default function Name` by stripping the export and assigning module.exports.default
    let mut default_export: Option<String> = None;
    if let Some(idx) = raw.find("export default function") {
        let after = &raw[idx + "export default function".len()..];
        let name: String = after
            .trim_start()
            .chars()
            .take_while(|c| c.is_alphanumeric() || *c == '_' || *c == '$')
            .collect();
        if !name.is_empty() {
            default_export = Some(name);
        }
    }

    let mut code = raw.replacen("export default function", "function", 1);
    if let Some(name) = default_export {
        code.push_str(&format!("\nmodule.exports.default = {};\n", name));
    }
    
    // Add CommonJS wrapper for module exports
    let prefix = "\nvar module = { exports: {} }; var exports = module.exports;\n";
    let suffix = "\n;try { globalThis.__last_module__ = module.exports; } catch (e) { /* noop */ }\n";
    Ok(format!("{}\n{}{}", prefix, code, suffix))
}

// Legacy: returns themed-styler version (kept for backward compatibility)
pub fn get_version() -> String {
    version().to_string()
}

// Explicit versions for modules used by Android JNI
pub fn get_themed_styler_version() -> String {
    themed_styler::version().to_string()
}

pub fn get_transpiler_version() -> String {
    transpiler_version().to_string()
}
