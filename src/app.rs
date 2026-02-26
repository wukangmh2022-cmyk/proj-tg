use std::path::Path;

use crate::agent::{render_markdown_report, run_agent};
use crate::config::{GlocalVisionConfig, DEFAULT_CONFIG_PATH};
use crate::telegram::{TdlibBackend, TelegramBackend, TelegramBackendKind};

pub enum AppCommand {
    Init,
    Run,
    Agent {
        channel_path: String,
        user_request: String,
    },
    Help,
}

impl AppCommand {
    pub fn from_args(args: Vec<String>) -> Self {
        match args.first().map(|s| s.as_str()) {
            Some("init") => Self::Init,
            Some("run") => Self::Run,
            Some("agent") => {
                if args.len() < 3 {
                    return Self::Help;
                }
                let channel_path = args[1].clone();
                let user_request = args[2..].join(" ");
                Self::Agent {
                    channel_path,
                    user_request,
                }
            }
            Some("help") | Some("-h") | Some("--help") => Self::Help,
            Some(_) | None => Self::Help,
        }
    }
}

pub struct App {
    backend: Box<dyn TelegramBackend>,
}

impl App {
    pub fn new() -> Self {
        Self {
            backend: Box::new(TdlibBackend::new()),
        }
    }

    pub fn execute(&mut self, command: AppCommand) -> Result<(), String> {
        match command {
            AppCommand::Init => self.init_config(),
            AppCommand::Run => self.run(),
            AppCommand::Agent {
                channel_path,
                user_request,
            } => self.agent(&channel_path, &user_request),
            AppCommand::Help => {
                Self::print_help();
                Ok(())
            }
        }
    }

    fn init_config(&self) -> Result<(), String> {
        let path = Path::new(DEFAULT_CONFIG_PATH);
        if path.exists() {
            return Err(format!(
                "Config already exists at {}",
                path.to_string_lossy()
            ));
        }

        GlocalVisionConfig::write_template(path)?;
        println!("Created {}", path.to_string_lossy());
        println!("Fill api_id/api_hash, then run: cargo run -- run");
        Ok(())
    }

    fn run(&self) -> Result<(), String> {
        let path = Path::new(DEFAULT_CONFIG_PATH);
        let cfg = GlocalVisionConfig::read_from_disk(path)?;

        println!("Starting {}", cfg.app_name);
        println!("Backend: {:?}", self.backend.kind());

        self.backend.validate(&cfg)?;
        self.backend.connect(&cfg)
    }

    fn agent(&self, channel_path: &str, user_request: &str) -> Result<(), String> {
        let report = run_agent(channel_path, user_request)?;
        println!("{}", render_markdown_report(&report));
        Ok(())
    }

    fn print_help() {
        println!("glocalVision custom Telegram starter");
        println!();
        println!("Commands:");
        println!("  init    Create glocalvision.toml template");
        println!("  run     Start with local config");
        println!(
            "  agent   Analyze channel signals by grep. Usage: cargo run -- agent <channel_path> <user_request>"
        );
        println!("  help    Show this help");
        println!();
        println!("TDLib feature flags:");
        println!("  cargo run --features tdlib-download -- run");
        println!("  cargo run --features tdlib-local -- run");
        println!("  cargo run --features tdlib-pkg-config -- run");
    }
}

#[allow(dead_code)]
fn _backend_name(kind: TelegramBackendKind) -> &'static str {
    match kind {
        TelegramBackendKind::Tdlib => "TDLib",
    }
}
