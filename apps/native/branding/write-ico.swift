import Foundation

struct IconImage {
    let size: Int
    let data: Data
}

func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data("write-ico: \(message)\n".utf8))
    exit(1)
}

func appendUInt16LE(_ value: Int, to data: inout Data) {
    data.append(UInt8(value & 0xff))
    data.append(UInt8((value >> 8) & 0xff))
}

func appendUInt32LE(_ value: Int, to data: inout Data) {
    data.append(UInt8(value & 0xff))
    data.append(UInt8((value >> 8) & 0xff))
    data.append(UInt8((value >> 16) & 0xff))
    data.append(UInt8((value >> 24) & 0xff))
}

let arguments = Array(CommandLine.arguments.dropFirst())
guard arguments.count >= 2 else {
    fail("usage: write-ico.swift OUTPUT SIZE=PNG [SIZE=PNG ...]")
}

let outputPath = arguments[0]
let images = arguments.dropFirst().map { argument -> IconImage in
    let parts = argument.split(separator: "=", maxSplits: 1).map(String.init)
    guard parts.count == 2, let size = Int(parts[0]), (1...256).contains(size) else {
        fail("invalid icon input '\(argument)'")
    }
    guard let image = FileManager.default.contents(atPath: parts[1]) else {
        fail("could not read '\(parts[1])'")
    }
    return IconImage(size: size, data: image)
}

guard Set(images.map(\.size)).count == images.count else {
    fail("icon sizes must be unique")
}

var output = Data()
appendUInt16LE(0, to: &output)
appendUInt16LE(1, to: &output)
appendUInt16LE(images.count, to: &output)

var imageOffset = 6 + images.count * 16
for image in images {
    output.append(image.size == 256 ? 0 : UInt8(image.size))
    output.append(image.size == 256 ? 0 : UInt8(image.size))
    output.append(0)
    output.append(0)
    appendUInt16LE(1, to: &output)
    appendUInt16LE(32, to: &output)
    appendUInt32LE(image.data.count, to: &output)
    appendUInt32LE(imageOffset, to: &output)
    imageOffset += image.data.count
}

for image in images {
    output.append(image.data)
}

do {
    try output.write(to: URL(fileURLWithPath: outputPath), options: .atomic)
} catch {
    fail("could not write '\(outputPath)': \(error.localizedDescription)")
}
