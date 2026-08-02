/**
 * 목업 상품 사진.
 *
 * API 가 붙으면 상품마다 `imageUrl` 이 내려온다. 그 전까지 화면이 회색
 * 네모로만 채워지면 배치를 판단하기 어려워서, 이름에 맞는 사진을 골라
 * 붙인다. Unsplash 이미지 CDN 을 그대로 쓰고 저장은 하지 않는다.
 *
 * 이 파일은 `src/mocks` 의 다른 파일과 함께 API 연동 시 삭제한다.
 */

/** 이름에 이 단어가 들어가면 짝지어진 사진을 쓴다. 위에서부터 먼저 맞는 것. */
const BY_KEYWORD: ReadonlyArray<readonly [RegExp, string]> = [
  [/스니커|조던|운동화|슈즈/, 'photo-1542291026-7eec264c27ff'],
  [/데님|자켓|청자켓/, 'photo-1495105787522-5334e3ffa0ef'],
  [/포토카드|아이돌|앨범/, 'photo-1520271348391-049dd132bb7c'],
  [/워치|시계/, 'photo-1523170335258-f5ed11844a49'],
  [/피규어|장난감/, 'photo-1608889175123-8ee362201f81'],
  [/지갑|가죽/, 'photo-1627123424574-724758594e93'],
  [/렌즈/, 'photo-1516035069371-29a1b244cc32'],
  [/카메라|필름/, 'photo-1510127034890-ba27508e9f1c'],
  [/가방|백팩/, 'photo-1553062407-98eeb64c6a62'],
  [/목걸이|주얼리|반지/, 'photo-1611652022419-a9419f74343d'],
  [/구두|로퍼/, 'photo-1560343090-f0409e92791a'],
  [/모자|캡/, 'photo-1556306535-0f09a537f0a3'],
  [/이어폰/, 'photo-1572569511254-d8f925fe2cbb'],
  [/헤드폰|헤드셋/, 'photo-1505740420928-5e560c06d30e'],
  [/키보드/, 'photo-1595044426077-d36d9236d54a'],
  [/스마트워치/, 'photo-1523275335684-37898b6baf30'],
  [/스마트폰|휴대폰|충전/, 'photo-1523206489230-c012c64b2b48'],
  [/코트|가디건|셔츠|니트/, 'photo-1495105787522-5334e3ffa0ef'],
  [/아트토이|키링|스탠드|포스터|굿즈/, 'photo-1608889175123-8ee362201f81'],
]

/** 키워드에 안 걸리는 이름이 쓸 사진. 이름 해시로 고정 배정한다. */
const FALLBACK = [
  'photo-1595950653106-6c9ebd614d3a',
  'photo-1524592094714-0f0654e20314',
  'photo-1553062407-98eeb64c6a62',
  'photo-1587836374828-4dbafa94cf0e',
  'photo-1560343090-f0409e92791a',
]

/** 같은 이름이면 항상 같은 사진이 나오도록 하는 문자열 해시. */
function hash(value: string) {
  let result = 0
  for (let index = 0; index < value.length; index += 1) {
    result = (result * 31 + value.charCodeAt(index)) % 100_000
  }
  return result
}

/**
 * 상품 이름으로 목업 사진 주소를 만든다.
 *
 * @param size 정사각형 기준 픽셀. 썸네일은 작게, 상세는 크게 받는다.
 */
export function mockProductImage(name: string, size = 480) {
  const matched = BY_KEYWORD.find(([pattern]) => pattern.test(name))?.[1]
  const photo = matched ?? FALLBACK[hash(name) % FALLBACK.length]
  return `https://images.unsplash.com/${photo}?w=${size}&h=${size}&fit=crop&auto=format&q=70`
}

/** 프로필 더미 사진. 사람 사진 몇 장을 이름 해시로 돌려쓴다. */
const AVATARS = [
  'photo-1543087903-1ac2ec7aa8c5',
  'photo-1517841905240-472988babdf9',
  'photo-1520271348391-049dd132bb7c',
]

/** 닉네임·가게명으로 프로필 사진 주소를 만든다. */
export function mockAvatarImage(seed: string, size = 240) {
  const photo = AVATARS[hash(seed) % AVATARS.length]
  return `https://images.unsplash.com/${photo}?w=${size}&h=${size}&fit=crop&auto=format&q=70`
}
