//! Mirror Signaling Server
//! 
//! Optional WebSocket signaling server for WebRTC peer connection.
//! Can be self-hosted or replaced with Firebase/saas solution.

use axum::{
    routing::get,
    Router,
};
use std::net::SocketAddr;
use tracing::{info, Level};
use tracing_subscriber;

#[tokio::main]
async fn main() {
    // Initialize logging
    tracing_subscriber::fmt()
        .with_max_level(Level::INFO)
        .init();

    info!("Starting Mirror Signaling Server");

    let app = Router::new()
        .route("/health", get(health_check));

    let addr = SocketAddr::from(([0, 0, 0, 0], 8080));
    info!("Listening on {}", addr);

    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}

async fn health_check() -> &'static str {
    "Mirror Signaling Server OK"
}
