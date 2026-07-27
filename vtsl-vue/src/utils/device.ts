/** 设备指纹：首次访问生成 UUID，LocalStorage 持久化（联调文档 §2.1） */
export function getDeviceId(): string {
  let id = localStorage.getItem('device_id')
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem('device_id', id)
  }
  return id
}
