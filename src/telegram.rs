use crate::config::GlocalVisionConfig;

#[derive(Debug, Clone, Copy)]
pub enum TelegramBackendKind {
    Tdlib,
}

pub trait TelegramBackend {
    fn kind(&self) -> TelegramBackendKind;
    fn validate(&self, cfg: &GlocalVisionConfig) -> Result<(), String>;
    fn connect(&self, cfg: &GlocalVisionConfig) -> Result<(), String>;
}

pub struct TdlibBackend;

impl TdlibBackend {
    pub fn new() -> Self {
        Self
    }

    fn is_tdlib_feature_enabled() -> bool {
        cfg!(any(
            feature = "tdlib-download",
            feature = "tdlib-local",
            feature = "tdlib-pkg-config"
        ))
    }
}

impl TelegramBackend for TdlibBackend {
    fn kind(&self) -> TelegramBackendKind {
        TelegramBackendKind::Tdlib
    }

    fn validate(&self, cfg: &GlocalVisionConfig) -> Result<(), String> {
        if cfg.api_id == "YOUR_TELEGRAM_API_ID" || cfg.api_id.is_empty() {
            return Err("api_id is not configured in glocalvision.toml".to_string());
        }

        if cfg.api_hash == "YOUR_TELEGRAM_API_HASH" || cfg.api_hash.is_empty() {
            return Err("api_hash is not configured in glocalvision.toml".to_string());
        }

        Ok(())
    }

    fn connect(&self, cfg: &GlocalVisionConfig) -> Result<(), String> {
        if !Self::is_tdlib_feature_enabled() {
            return Err(
                "TDLib backend selected but no TDLib feature enabled. Use e.g. `cargo run --features tdlib-download -- run`".to_string()
            );
        }

        println!("TDLib wiring is ready for custom business modules.");
        println!(
            "Config snapshot: app_name={}, device_model={}, lang={}, test_dc={}",
            cfg.app_name, cfg.device_model, cfg.language_code, cfg.use_test_dc
        );
        println!("Next step: add auth/session/message pipelines in src/telegram.rs.");
        Ok(())
    }
}
