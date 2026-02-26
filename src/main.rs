mod agent;
mod app;
mod config;
mod date;
mod telegram;

use app::{App, AppCommand};

fn main() {
    if let Err(err) = run() {
        eprintln!("glocalVision failed: {err}");
        std::process::exit(1);
    }
}

fn run() -> Result<(), String> {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let command = AppCommand::from_args(args);
    let mut app = App::new();
    app.execute(command)
}
