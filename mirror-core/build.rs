use std::env;

fn main() {
    // Get the target architecture
    let target = env::var("TARGET").unwrap();
    
    println!("cargo:rerun-if-changed=src");
    println!("cargo:rerun-if-changed=build.rs");
    
    // Architecture-specific settings
    match target.as_str() {
        "aarch64-linux-android" => {
            println!("cargo:rustc-cfg=target_arch=\"aarch64\"");
        }
        "armv7-linux-androideabi" => {
            println!("cargo:rustc-cfg=target_arch=\"arm\"");
        }
        "x86_64-linux-android" => {
            println!("cargo:rustc-cfg=target_arch=\"x86_64\"");
        }
        "i686-linux-android" => {
            println!("cargo:rustc-cfg=target_arch=\"x86\"");
        }
        _ => {}
    }
    
    // Link against Android log library for logging
    println!("cargo:rustc-link-lib=log");
}
