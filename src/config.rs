use std::fs;
use std::path::Path;

pub const DEFAULT_CONFIG_PATH: &str = "glocalvision.toml";

#[derive(Debug, Clone)]
pub struct GlocalVisionConfig {
    pub app_name: String,
    pub api_id: String,
    pub api_hash: String,
    pub device_model: String,
    pub system_version: String,
    pub language_code: String,
    pub use_test_dc: bool,
}

impl Default for GlocalVisionConfig {
    fn default() -> Self {
        Self {
            app_name: "glocalVision".to_string(),
            api_id: "YOUR_TELEGRAM_API_ID".to_string(),
            api_hash: "YOUR_TELEGRAM_API_HASH".to_string(),
            device_model: "glocalVision-dev".to_string(),
            system_version: "mobile".to_string(),
            language_code: "en".to_string(),
            use_test_dc: false,
        }
    }
}

impl GlocalVisionConfig {
    pub fn write_template(path: &Path) -> Result<(), String> {
        let template = Self::default().as_toml_like_template();
        fs::write(path, template).map_err(|e| format!("write config failed: {e}"))
    }

    pub fn read_from_disk(path: &Path) -> Result<Self, String> {
        let input = fs::read_to_string(path)
            .map_err(|e| format!("read config {} failed: {e}", path.to_string_lossy()))?;

        let mut cfg = Self::default();

        for (idx, raw) in input.lines().enumerate() {
            let line = raw.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }

            let (k, v) = line
                .split_once('=')
                .ok_or_else(|| format!("invalid config at line {}: {}", idx + 1, line))?;

            let key = k.trim();
            let val = v.trim().trim_matches('"');

            match key {
                "app_name" => cfg.app_name = val.to_string(),
                "api_id" => cfg.api_id = val.to_string(),
                "api_hash" => cfg.api_hash = val.to_string(),
                "device_model" => cfg.device_model = val.to_string(),
                "system_version" => cfg.system_version = val.to_string(),
                "language_code" => cfg.language_code = val.to_string(),
                "use_test_dc" => {
                    cfg.use_test_dc = parse_bool(val)
                        .ok_or_else(|| format!("invalid bool at line {}: {}", idx + 1, val))?;
                }
                _ => {}
            }
        }

        Ok(cfg)
    }

    fn as_toml_like_template(&self) -> String {
        format!(
            "# glocalVision local config\n\
# Get api_id/api_hash from https://my.telegram.org\n\n\
app_name = \"{}\"\n\
api_id = \"{}\"\n\
api_hash = \"{}\"\n\
device_model = \"{}\"\n\
system_version = \"{}\"\n\
language_code = \"{}\"\n\
use_test_dc = {}\n",
            self.app_name,
            self.api_id,
            self.api_hash,
            self.device_model,
            self.system_version,
            self.language_code,
            self.use_test_dc
        )
    }
}

fn parse_bool(raw: &str) -> Option<bool> {
    match raw {
        "true" => Some(true),
        "false" => Some(false),
        _ => None,
    }
}
