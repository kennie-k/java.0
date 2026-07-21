'use client'
import { motion } from 'framer-motion'

interface Props {
  children: React.ReactNode
  index?: number
  className?: string
}

export default function RevealCard({ children, index = 0, className }: Props) {
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: 24, scale: 0.94, rotate: index % 2 === 0 ? -1.5 : 1.5 }}
      whileInView={{ opacity: 1, y: 0, scale: 1, rotate: 0 }}
      viewport={{ once: true, margin: '-40px' }}
      transition={{ type: 'spring', stiffness: 260, damping: 18, delay: (index % 6) * 0.05 }}
      whileHover={{ y: -4, transition: { duration: 0.15, ease: 'easeOut' } }}
    >
      {children}
    </motion.div>
  )
}
