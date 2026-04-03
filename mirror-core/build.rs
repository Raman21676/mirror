use std::env;

fn main() {
    // Get the target architecture
    let target = env::var("TARGET").unwrap();
    
    println!("cargo:rerun-if-changed=src");
    println!("cargo:rerun-if-changed=build.rs");
    
    // Link against Android log library for logging
    println!("cargo:rustc-link-lib=log");
}
