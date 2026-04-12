import { useState, useRef, useEffect } from 'react'
import { Camera, X, Upload } from 'lucide-react'

interface ImageUploadProps {
  currentImage?: string
  onImageChange: (dataUrl: string) => void
  size?: number
  borderRadius?: number | string
  label?: string
}

export function ImageUpload({
  currentImage,
  onImageChange,
  size = 80,
  borderRadius = '50%',
  label,
}: ImageUploadProps) {
  const [preview, setPreview] = useState<string | undefined>(currentImage)
  const [isDragging, setIsDragging] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    setPreview(currentImage)
  }, [currentImage])

  const handleFile = (file: File) => {
    if (!file.type.startsWith('image/')) return
    const reader = new FileReader()
    reader.onload = (e) => {
      const dataUrl = e.target?.result as string
      setPreview(dataUrl)
      onImageChange(dataUrl)
    }
    reader.readAsDataURL(file)
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) handleFile(file)
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
    const file = e.dataTransfer.files?.[0]
    if (file) handleFile(file)
  }

  const handleRemove = (e: React.MouseEvent) => {
    e.stopPropagation()
    setPreview(undefined)
    onImageChange('')
    if (inputRef.current) inputRef.current.value = ''
  }

  const containerStyle: React.CSSProperties = {
    position: 'relative',
    width: size,
    height: size,
    borderRadius,
    cursor: 'pointer',
    flexShrink: 0,
  }

  const overlayStyle: React.CSSProperties = {
    position: 'absolute',
    inset: 0,
    borderRadius,
    background: 'rgba(0,0,0,0.5)',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    opacity: 0,
    transition: 'opacity 0.2s ease',
    color: 'white',
    fontSize: Math.max(10, size * 0.12),
    fontWeight: 600,
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
      {label && (
        <span style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 500 }}>{label}</span>
      )}
      <div
        style={containerStyle}
        onClick={() => inputRef.current?.click()}
        onDragOver={(e) => { e.preventDefault(); setIsDragging(true) }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
      >
        {preview ? (
          <>
            <img
              src={preview}
              alt="Preview"
              style={{ width: '100%', height: '100%', borderRadius, objectFit: 'cover' }}
            />
            <div style={{ ...overlayStyle, opacity: isDragging ? 1 : undefined }}>
              <Upload size={Math.max(14, size * 0.2)} />
            </div>
            {typeof onImageChange === 'function' && (
              <button
                onClick={handleRemove}
                style={{
                  position: 'absolute',
                  top: -6,
                  right: -6,
                  width: Math.max(20, size * 0.25),
                  height: Math.max(20, size * 0.25),
                  borderRadius: '50%',
                  border: '2px solid var(--bg-secondary)',
                  background: '#ef4444',
                  color: 'white',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  padding: 0,
                  zIndex: 10,
                }}
              >
                <X size={Math.max(10, size * 0.13)} />
              </button>
            )}
          </>
        ) : (
          <div style={{
            width: '100%',
            height: '100%',
            borderRadius,
            border: `2px dashed ${isDragging ? 'var(--accent)' : 'var(--border)'}`,
            background: 'var(--bg-tertiary)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 4,
            transition: 'border-color 0.2s ease',
            color: 'var(--text-muted)',
          }}>
            <Camera size={Math.max(16, size * 0.22)} />
            <span style={{ fontSize: Math.max(9, size * 0.11), fontWeight: 500 }}>Upload</span>
          </div>
        )}
      </div>

      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        onChange={handleInputChange}
        style={{ display: 'none' }}
      />
    </div>
  )
}
