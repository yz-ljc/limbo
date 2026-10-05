'use strict'
// Run against the supplied worlds/limbo.schematic (nine signs), on a loopback test server.
const mc = require('minecraft-protocol')
const assert = require('node:assert/strict')
const versions = process.argv.slice(2)
if (!versions.length) versions.push('1.8', '1.9', '1.9.4', '1.12.2', '1.13.2', '1.16.5', '1.19.2', '1.20.1', '1.21.4', '26.1')

async function probe(version, index) {
  return new Promise((resolve, reject) => {
    const client = mc.createClient({ host: '127.0.0.1', port: Number(process.env.LIMBO_PORT || 25567),
      username: 'SignProbe' + index, version, auth: 'offline', hideErrors: true })
    const signs = new Set()
    let welcome = false, help = false, heartbeats = 0, done = false
    const timeout = setTimeout(() => finish(new Error('timeout: ' + JSON.stringify({ signs: signs.size, welcome, help }))), 20000)
    function finish(error) {
      if (done) return
      done = true
      clearTimeout(timeout)
      client.end()
      if (error) reject(new Error(version + ': ' + error.message))
      else resolve({ version, signs: signs.size, welcome, help })
    }
    client.on('error', finish)
    client.on('end', reason => { if (!done) finish(new Error('disconnected: ' + reason)) })
    client.on('packet', (data, meta) => {
      try {
        if (meta.state !== 'play') return
        const json = JSON.stringify(data)
        if (meta.name === 'update_sign' || meta.name === 'tile_entity_data') {
          if (json.includes('/lobby')) {
            signs.add(JSON.stringify(data.location ?? data.position))
            assert.ok(json.includes('aqua') || json.includes('red'), 'Sign formatting was lost')
          }
        }
        if (meta.name === 'chat' || meta.name === 'system_chat') {
          if (json.includes('You were spawned in Limbo.')) {
            welcome = true
            assert.ok(json.includes('red'))
            client.chat('/limbo')
          }
          if (json.includes('Watch out, though, as there are things that live in Limbo.')) {
            help = true
            assert.ok(json.includes('dark_red'))
          }
        }
        if (meta.name === 'position' && data.teleportId !== undefined)
          client.write('teleport_confirm', { teleportId: data.teleportId })
        if (meta.name === 'keep_alive') heartbeats++
        if (signs.size === 9 && welcome && help && heartbeats >= 2) finish()
      } catch (error) { finish(error) }
    })
  })
}

;(async () => {
  let passed = 0
  for (let offset = 0; offset < versions.length; offset += 4) {
    const results = await Promise.allSettled(versions.slice(offset, offset + 4).map((v, i) => probe(v, offset + i)))
    for (const result of results) {
      if (result.status === 'fulfilled') { passed++; console.log('PASS', JSON.stringify(result.value)) }
      else { process.exitCode = 1; console.error('FAIL', result.reason.message) }
    }
  }
  console.log(passed + '/' + versions.length + ' sign/chat versions passed')
})()
